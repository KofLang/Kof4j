// vkchain64.c — M36 FASE C: matvec int64 com W residente.
// Contrato:
//   int  vkchain64_init(const char* spv_path)      → 0 ok (pipeline matvec)
//   const char* vkchain64_fail_reason(void)
//   void* vkchain64_mapped_w(void)                 → ptr host p/ escrever W
//   int   vkchain64_set_shape(int m, int k)        → dimensiona W/x/y (re-aloca
//           se precisar; W mapeado persistente HOST_VISIBLE|COHERENT)
//   int   vkchain64_matvec(long* x, long* y, int m, int k)
//           → 0 ok: lê W do buffer mapeado (sem copiar), x copiado, y copiado
//     != 0 (caller usa golden CPU)
// matvec64.comp: binding0=w readonly, binding1=x, binding2=y writeonly,
// push (m,k), local_size 64 (1 thread/row).
// Memória: heap 0 device-local 1.75GiB budget ~978MB; big W usa HOST_VISIBLE
// (host mem via PCIe: leitura ~6-12GB/s no Polaris12 — 25x vs CPU 12s/token).
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

static VkInstance inst; static VkDevice dev; static VkQueue q; static int qfam;
static VkPhysicalDevice phys;
static VkPipeline pipe; static VkPipelineLayout pl;
// M36.1: pipeline do matvecw32 (W i32) — mesmo layout (3 SSBOs + push 24B)
static VkPipeline pipe32;
// M36.3: cadeia split (5 SSBOs) — layout/dset/pipeline próprios
static VkPipeline pipeSplit; static VkPipelineLayout pl5;
static VkDescriptorPool dpool5; static VkDescriptorSet dset5;
static VkDescriptorSetLayout dsl5;
static VkDescriptorPool dpool; static VkDescriptorSet dset;
static VkDescriptorSetLayout dsl;
static VkBuffer wbuf; static VkDeviceMemory wmem; static void* wmap;
static VkBuffer xbuf; static VkDeviceMemory xmem; static void* xmap;
static VkBuffer ybuf; static VkDeviceMemory ymem; static void* ymap;
// M36 FASE C2b: cache de W residentes por id (pesos por layer ficam no
// buffer entre calls — zero copia por forward)
#define WMAX 192
static VkBuffer wbufs[WMAX]; static VkDeviceMemory wmems[WMAX];
static void* wmaps[WMAX];
static long wcap[WMAX];   // elems por id (0 = vazio)
// M36.1: caminho i32 — buffers W/x/y de 4 bytes (metade do PCIe)
static VkBuffer wbufs32[WMAX]; static VkDeviceMemory wmems32[WMAX];
static void* wmaps32[WMAX];
static long wcap32[WMAX];
static VkBuffer xbuf32; static VkDeviceMemory xmem32; static void* xmap32;
static long xcap32;
static VkBuffer ybuf32; static VkDeviceMemory ymem32; static void* ymap32;
static long ycap32;
// M36.3: caminho split pre-computado — wh/wl i32 no warm (por id), xh/xl
// i32 por dispatch, y i64. 5 SSBOs (bindings 0..4), zero div i64 por termo.
static VkBuffer whbufs[WMAX]; static VkDeviceMemory whmems[WMAX];
static void* whmaps[WMAX];
static long whcap[WMAX];
static VkBuffer wlbufs[WMAX]; static VkDeviceMemory wlmems[WMAX];
static void* wlmaps[WMAX];
static long wlcap[WMAX];
static VkBuffer xhbuf; static VkDeviceMemory xhmem; static void* xhmap;
static long xhcap;
static VkBuffer xlbuf; static VkDeviceMemory xlmem; static void* xlmap;
static long xlcap;
static VkCommandPool cpool; static VkCommandBuffer cmd;
static VkFence fence;
static char errbuf[256] = "not initialized";
static int inited = 0;
static int wcapElems = 0;   // capacidade atual do W (elems int64)
static int xcapElems = 0;
static int ycapElems = 0;
static int curM = 0, curK = 0;

#define CK(x, msg) do { VkResult r=(x); if(r!=VK_SUCCESS){ snprintf(errbuf,sizeof errbuf,"%s (rc=%d)",msg,r); return 1; } } while(0)

const char* vkchain64_fail_reason(void){ return errbuf; }

// aloca buffer+mem host-visible coherent e mapeia
static int allocBuffer(VkBuffer* buf, VkDeviceMemory* mem, void** map, VkDeviceSize bytes){
    VkBufferCreateInfo bci={VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,0,0,bytes,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,VK_SHARING_MODE_EXCLUSIVE,0,0};
    CK(vkCreateBuffer(dev,&bci,0,buf), "buffer");
    VkMemoryRequirements req; vkGetBufferMemoryRequirements(dev,*buf,&req);
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(phys,&mp);
    int mi=-1;
    for (uint32_t t=0;t<mp.memoryTypeCount;t++)
        if ((req.memoryTypeBits&(1u<<t))&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){mi=(int)t;break;}
    if (mi<0){ snprintf(errbuf,sizeof errbuf,"sem mem host-visible"); return 1; }
    VkMemoryAllocateInfo mai={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,0,req.size,(uint32_t)mi};
    CK(vkAllocateMemory(dev,&mai,0,mem), "alloc mem");
    CK(vkBindBufferMemory(dev,*buf,*mem,0), "bind mem");
    CK(vkMapMemory(dev,*mem,0,bytes,0,map), "map");
    return 0;
}

static void freeBuffer(VkBuffer* buf, VkDeviceMemory* mem, void** map){
    if (*map) vkUnmapMemory(dev,*mem);
    if (*buf) vkDestroyBuffer(dev,*buf,0);
    if (*mem) vkFreeMemory(dev,*mem,0);
    *buf=0; *mem=0; *map=0;
}

static int init_common(const char* spv_path, int nbinds);

int vkchain64_init(const char* spv_path){
    return init_common(spv_path, 3);
}

static int init_common(const char* spv_path, int nbinds){
    if (inited) return 0;
    VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"kof",0,0,0,VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai,0,0,0,0};
    CK(vkCreateInstance(&ici,0,&inst), "instance");
    uint32_t n=0;
    CK(vkEnumeratePhysicalDevices(inst,&n,0), "enum0");
    if (n==0){ snprintf(errbuf,sizeof errbuf,"nenhum physical device"); return 1; }
    VkPhysicalDevice physv[4];
    CK(vkEnumeratePhysicalDevices(inst,&n,physv), "enum");
    phys = physv[0];
    uint32_t qn=0; vkGetPhysicalDeviceQueueFamilyProperties(phys,&qn,0);
    VkQueueFamilyProperties qf[8]; vkGetPhysicalDeviceQueueFamilyProperties(phys,&qn,qf);
    qfam=0; while(qfam<(int)qn && !(qf[qfam].queueFlags&VK_QUEUE_COMPUTE_BIT)) qfam++;
    if (qfam>=(int)qn){ snprintf(errbuf,sizeof errbuf,"sem compute queue"); return 1; }
    float prio=1.0f;
    VkDeviceQueueCreateInfo qci={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,(uint32_t)qfam,1,&prio};
    VkDeviceCreateInfo dci={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&qci,0,0,0,0};
    CK(vkCreateDevice(phys,&dci,0,&dev), "device");
    vkGetDeviceQueue(dev,(uint32_t)qfam,0,&q);

    // SPIR-V
    FILE* f=fopen(spv_path,"rb");
    if(!f){ snprintf(errbuf,sizeof errbuf,"spv nao abriu: %s",spv_path); return 1; }
    fseek(f,0,SEEK_END); long sz=ftell(f); fseek(f,0,SEEK_SET);
    uint32_t* code=malloc((size_t)sz);
    if (fread(code,1,(size_t)sz,f)!=(size_t)sz){ snprintf(errbuf,sizeof errbuf,"spv leitura falhou"); return 1; }
    fclose(f);
    VkShaderModuleCreateInfo smci={VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,0,0,(size_t)sz,code};
    VkShaderModule sm;
    CK(vkCreateShaderModule(dev,&smci,0,&sm), "shader module");
    free(code);

    // desc layout: 3 SSBOs (matvec64) ou 5 (matvecsplit)
    if (nbinds != 3 && nbinds != 5) { snprintf(errbuf,sizeof errbuf,"nbinds?"); return 1; }
    VkDescriptorSetLayoutBinding binds[5];
    for (int b=0;b<nbinds;b++) binds[b]=(VkDescriptorSetLayoutBinding){(uint32_t)b,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,1,VK_SHADER_STAGE_COMPUTE_BIT,0};
    VkDescriptorSetLayoutCreateInfo dli={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,0,0,(uint32_t)nbinds,binds};
    CK(vkCreateDescriptorSetLayout(dev,&dli,0,&dsl), "desc layout");
    VkPushConstantRange pcr={VK_SHADER_STAGE_COMPUTE_BIT,0,24};
    VkPipelineLayoutCreateInfo pli={VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,0,0,1,&dsl,1,&pcr};
    CK(vkCreatePipelineLayout(dev,&pli,0,&pl), "pipe layout");
    VkPipelineShaderStageCreateInfo stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,0,0,VK_SHADER_STAGE_COMPUTE_BIT,sm,"main",0};
    VkComputePipelineCreateInfo pci={VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,0,0,stage,pl,0,0};
    CK(vkCreateComputePipelines(dev,0,1,&pci,0,&pipe), "pipeline");

    // M36.1: pipeline w32 (W i32) com o MESMO layout (3 SSBOs + push 24B).
    // Falha é não-fatal: o wrun32 degrada (rc != 0 → caller usa CPU).
    pipe32 = 0;
    const char* spv32 = getenv("KOF_GPU_SPV64_W32");
    if (spv32) {
        FILE* f32 = fopen(spv32, "rb");
        if (f32) {
            fseek(f32, 0, SEEK_END); long sz32 = ftell(f32); fseek(f32, 0, SEEK_SET);
            uint32_t* code32 = malloc((size_t)sz32);
            if (fread(code32, 1, (size_t)sz32, f32) == (size_t)sz32) {
                VkShaderModuleCreateInfo smci32 = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,0,0,(size_t)sz32,code32};
                VkShaderModule sm32;
                if (vkCreateShaderModule(dev,&smci32,0,&sm32) == VK_SUCCESS) {
                    VkPipelineShaderStageCreateInfo st32 = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,0,0,VK_SHADER_STAGE_COMPUTE_BIT,sm32,"main",0};
                    VkComputePipelineCreateInfo pci32 = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,0,0,st32,pl,0,0};
                    if (vkCreateComputePipelines(dev,0,1,&pci32,0,&pipe32) != VK_SUCCESS) pipe32 = 0;
                }
            }
            free(code32);
            fclose(f32);
        }
    }

    // M36.3: cadeia split (5 SSBOs). Não-fatal: o wrunsp degrada.
    pipeSplit = 0; pl5 = 0; dset5 = 0;
    const char* spvsp = getenv("KOF_GPU_SPV64_SPLIT");
    if (spvsp) {
        FILE* fsp = fopen(spvsp, "rb");
        if (fsp) {
            fseek(fsp, 0, SEEK_END); long szsp = ftell(fsp); fseek(fsp, 0, SEEK_SET);
            uint32_t* codesp = malloc((size_t)szsp);
            if (fread(codesp, 1, (size_t)szsp, fsp) == (size_t)szsp) {
                VkShaderModuleCreateInfo smcisp = {VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,0,0,(size_t)szsp,codesp};
                VkShaderModule smsp;
                if (vkCreateShaderModule(dev,&smcisp,0,&smsp) == VK_SUCCESS) {
                    VkDescriptorSetLayoutBinding binds5[5];
                    for (int b=0;b<5;b++) binds5[b]=(VkDescriptorSetLayoutBinding){(uint32_t)b,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,1,VK_SHADER_STAGE_COMPUTE_BIT,0};
                    VkDescriptorSetLayoutCreateInfo dli5={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,0,0,5,binds5};
                    if (vkCreateDescriptorSetLayout(dev,&dli5,0,&dsl5) == VK_SUCCESS) {
                        VkPipelineLayoutCreateInfo pli5={VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,0,0,1,&dsl5,1,&pcr};
                        if (vkCreatePipelineLayout(dev,&pli5,0,&pl5) == VK_SUCCESS) {
                            VkPipelineShaderStageCreateInfo stsp = {VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,0,0,VK_SHADER_STAGE_COMPUTE_BIT,smsp,"main",0};
                            VkComputePipelineCreateInfo pcisp = {VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,0,0,stsp,pl5,0,0};
                            if (vkCreateComputePipelines(dev,0,1,&pcisp,0,&pipeSplit) == VK_SUCCESS) {
                                VkDescriptorPoolSize ps5={VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,5};
                                VkDescriptorPoolCreateInfo dpi5={VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,0,0,1,1,&ps5};
                                if (vkCreateDescriptorPool(dev,&dpi5,0,&dpool5) == VK_SUCCESS) {
                                    VkDescriptorSetAllocateInfo dsai5={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,0,dpool5,1,&dsl5};
                                    vkAllocateDescriptorSets(dev,&dsai5,&dset5);
                                }
                            }
                        }
                    }
                }
            }
            free(codesp);
            fclose(fsp);
        }
    }

    // desc pool + set (atualizado em set_shape / wrunsp)
    VkDescriptorPoolSize ps={VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,(uint32_t)nbinds};
    VkDescriptorPoolCreateInfo dpi={VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,0,0,1,1,&ps};
    CK(vkCreateDescriptorPool(dev,&dpi,0,&dpool), "desc pool");
    VkDescriptorSetAllocateInfo dsai={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,0,dpool,1,&dsl};
    CK(vkAllocateDescriptorSets(dev,&dsai,&dset), "desc set");

    // cmd pool/buffer + fence
    VkCommandPoolCreateInfo cpi={VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,0,0,(uint32_t)qfam};
    CK(vkCreateCommandPool(dev,&cpi,0,&cpool), "cmd pool");
    VkCommandBufferAllocateInfo cbai={VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,0,cpool,VK_COMMAND_BUFFER_LEVEL_PRIMARY,1};
    CK(vkAllocateCommandBuffers(dev,&cbai,&cmd), "cmd buf");
    VkFenceCreateInfo fci={VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,0,0};
    CK(vkCreateFence(dev,&fci,0,&fence), "fence");
    inited=1;
    snprintf(errbuf,sizeof errbuf,"ok");
    return 0;
}

// dimensiona x[k], y[m] (W central fica p/ API set_shape/load_w clássica)
static int set_shape_internal(int m, int k){
    if ((long)k > xcapElems){
        freeBuffer(&xbuf,&xmem,&xmap);
        if (allocBuffer(&xbuf,&xmem,&xmap,(VkDeviceSize)k*8)) return -2;
        xcapElems=k;
    }
    if ((long)m > ycapElems){
        freeBuffer(&ybuf,&ymem,&ymap);
        if (allocBuffer(&ybuf,&ymem,&ymap,(VkDeviceSize)m*8)) return -2;
        ycapElems=m;
    }
    return 0;
}

// dimensiona W[m×k], x[k], y[m] (re-aloca buffers que não comportam)
int vkchain64_set_shape(int m, int k){
    if (!inited) return -1;
    curM=m; curK=k;
    if ((long)m*k > wcapElems){
        freeBuffer(&wbuf,&wmem,&wmap);
        if (allocBuffer(&wbuf,&wmem,&wmap,(VkDeviceSize)m*k*8)) return -2;
        wcapElems=m*k;
    }
    if (set_shape_internal(m, k)) return -2;
    // desc set com os buffers atuais
    VkDescriptorBufferInfo dbi[3];
    VkWriteDescriptorSet wds[3];
    dbi[0]=(VkDescriptorBufferInfo){wbuf,0,(VkDeviceSize)wcapElems*8};
    dbi[1]=(VkDescriptorBufferInfo){xbuf,0,(VkDeviceSize)xcapElems*8};
    dbi[2]=(VkDescriptorBufferInfo){ybuf,0,(VkDeviceSize)ycapElems*8};
    for (int i=0;i<3;i++)
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    vkUpdateDescriptorSets(dev,3,wds,0,0);
    return 0;
}

void* vkchain64_mapped_w(void){ return wmap; }

// copia long[] w (m×k) pro buffer W mapeado — DMA host→host-visible
int vkchain64_load_w(long* w, int m, int k){
    if (!inited) return -1;
    if (!wmap) return -2;
    if ((long)m*k > wcapElems) return -3;  // set_shape primeiro
    memcpy(wmap, w, (size_t)m*k*8);
    return 0;
}

// FASE C2b: W residente por id — cria/redimensiona buffer[id] e copia w.
// Depois de wput, wrun(id,...) usa o buffer sem nova copia.
int vkchain64_wput(int id, long* w, int m, int k){
    if (!inited) return -1;
    if (id < 0 || id >= WMAX) return -2;
    if ((long)m*k <= 0) return -3;
    if (wcap[id] < (long)m*k){
        // (re)aloca: descarta o antigo se existir
        if (wbufs[id]){ 
            vkUnmapMemory(dev, wmems[id]);
            vkDestroyBuffer(dev, wbufs[id], 0);
            vkFreeMemory(dev, wmems[id], 0);
            wbufs[id]=0; wmems[id]=0; wmaps[id]=0; wcap[id]=0;
        }
        if (allocBuffer(&wbufs[id], &wmems[id], &wmaps[id], (VkDeviceSize)m*k*8)) return -4;
        wcap[id] = (long)m*k;
    }
    memcpy(wmaps[id], w, (size_t)m*k*8);
    return 0;
}

// FASE C2b: matvec com W residente do id — bind desc apontando buffer[id]
// + dispatch + readback y. x copiado por call (k*8 bytes ≤ 45KB).
int vkchain64_wrun(int id, long* x, long* y, int m, int k, long long div){
    if (!inited) return -1;
    if (id < 0 || id >= WMAX) return -2;
    if (!wbufs[id] || wcap[id] < (long)m*k) return -3;
    if ((long)k > xcapElems || (long)m > ycapElems){
        if (set_shape_internal(m, k)) return -4;
    }
    memcpy(xmap, x, (size_t)k*8);
    memset(ymap, 0, (size_t)m*8);
    if (getenv("KOF_MV64_TRACE")) {
        fprintf(stderr, "wrun id=%d m=%d k=%d x0=%ld x1=%ld ygold0=%ld\n",
                id, m, k, x[0], x[1], ((long*)wmaps[id])[0]*x[0]);
    }
    // rebind descriptor: w = buffer[id]
    VkDescriptorBufferInfo dbi[3];
    VkWriteDescriptorSet wds[3];
    dbi[0]=(VkDescriptorBufferInfo){wbufs[id],0,(VkDeviceSize)wcap[id]*8};
    dbi[1]=(VkDescriptorBufferInfo){xbuf,0,(VkDeviceSize)xcapElems*8};
    dbi[2]=(VkDescriptorBufferInfo){ybuf,0,(VkDeviceSize)ycapElems*8};
    for (int i=0;i<3;i++)
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    vkUpdateDescriptorSets(dev,3,wds,0,0);

    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -5; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipe);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl,0,1,&dset,0,0);
    // push 24B: m,k,divId,pad + div i64 — o matvecsim64 lê divId/div; os
    // shaders antigos (matvec64) ignoram pcs[2..5]
    int divId = (div == 1000000000LL) ? 0 : ((div == 1000000LL) ? 1 : 2);
    int pcs[6] = {m, k, divId, 0, 0, 0};
    memcpy(pcs+4, &div, 8);
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,24,pcs);
    vkCmdDispatch(cmd,(uint32_t)m,1,1);
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -5; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -5; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,60000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -5; }
    vkResetFences(dev,1,&fence);
    memcpy(y, ymap, (size_t)m*8);
    if (getenv("KOF_MV64_TRACE")) {
        fprintf(stderr, "wrunDONE id=%d y0=%ld y1=%ld\n", id, y[0], y[1]);
    }
    return 0;
}

// M36.1: W residente i32 — igual ao wput mas buffers de 4B/elem
int vkchain64_wput32(int id, int* w, int m, int k){
    if (!inited) return -1;
    if (id < 0 || id >= WMAX) return -2;
    if ((long)m*k <= 0) return -3;
    if (wcap32[id] < (long)m*k){
        if (wbufs32[id]){
            vkUnmapMemory(dev, wmems32[id]);
            vkDestroyBuffer(dev, wbufs32[id], 0);
            vkFreeMemory(dev, wmems32[id], 0);
            wbufs32[id]=0; wmems32[id]=0; wmaps32[id]=0; wcap32[id]=0;
        }
        if (allocBuffer(&wbufs32[id], &wmems32[id], &wmaps32[id], (VkDeviceSize)m*k*4)) return -4;
        wcap32[id] = (long)m*k;
    }
    memcpy(wmaps32[id], w, (size_t)m*k*4);
    return 0;
}

// M36.1: matvec com W i32 residente — x/y continuam i64 (o residuo nano
// passa de 2^31; o W domina o PCIe). div i64 por push. O SPV precisa ser o
// matvecw32 (KOF_GPU_SPV64 — o pipeline é carregado no init da lib).
int vkchain64_wrun32(int id, long* x, long* y, int m, int k, long long div){
    if (!inited) return -1;
    if (!pipe32) return -6;   // SPV w32 não disponível no init
    if (id < 0 || id >= WMAX) return -2;
    if (!wbufs32[id] || wcap32[id] < (long)m*k) return -3;
    if ((long)k > xcap32){
        if (xbuf32){ vkUnmapMemory(dev, xmem32); vkDestroyBuffer(dev, xbuf32, 0); vkFreeMemory(dev, xmem32, 0); }
        if (allocBuffer(&xbuf32, &xmem32, &xmap32, (VkDeviceSize)k*8)) return -4;
        xcap32 = k;
    }
    if ((long)m > ycap32){
        if (ybuf32){ vkUnmapMemory(dev, ymem32); vkDestroyBuffer(dev, ybuf32, 0); vkFreeMemory(dev, ymem32, 0); }
        if (allocBuffer(&ybuf32, &ymem32, &ymap32, (VkDeviceSize)m*8)) return -4;
        ycap32 = m;
    }
    memcpy(xmap32, x, (size_t)k*8);
    memset(ymap32, 0, (size_t)m*8);
    VkDescriptorBufferInfo dbi[3];
    VkWriteDescriptorSet wds[3];
    dbi[0]=(VkDescriptorBufferInfo){wbufs32[id],0,(VkDeviceSize)wcap32[id]*4};
    dbi[1]=(VkDescriptorBufferInfo){xbuf32,0,(VkDeviceSize)xcap32*8};
    dbi[2]=(VkDescriptorBufferInfo){ybuf32,0,(VkDeviceSize)ycap32*8};
    for (int i=0;i<3;i++)
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    vkUpdateDescriptorSets(dev,3,wds,0,0);

    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -5; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipe32);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl,0,1,&dset,0,0);
    // push: m,k,divId,pad (16B) + div (8B) = 24B — divId p/ o matvecsim
    // (0 = 1e9, 1 = 1e6, 2 = 1e12); os outros shaders ignoram pcs[2]
    int divId = (div == 1000000000LL) ? 0 : ((div == 1000000LL) ? 1 : 2);
    int pcs[6] = {m, k, divId, 0, 0, 0};
    memcpy(pcs+4, &div, 8);
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,24,pcs);
    vkCmdDispatch(cmd,(uint32_t)m,1,1);
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -5; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -5; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,60000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -5; }
    vkResetFences(dev,1,&fence);
    memcpy(y, ymap32, (size_t)m*8);
    return 0;
}

// M36.3: W pré-split por id — wh = w/1000, wl = w%1000 (i32: |wh| < 2^31).
// Dois buffers independentes; o wrunsp binda os dois.
int vkchain64_wputsp(int id, int* wh, int* wl, int m, int k){
    if (!inited) return -1;
    if (!pipeSplit) return -6;  // SPV split não disponível no init
    if (id < 0 || id >= WMAX) return -2;
    if ((long)m*k <= 0) return -3;
    if (whcap[id] < (long)m*k){
        if (whbufs[id]){
            vkUnmapMemory(dev, whmems[id]);
            vkDestroyBuffer(dev, whbufs[id], 0);
            vkFreeMemory(dev, whmems[id], 0);
            whbufs[id]=0; whmems[id]=0; whmaps[id]=0; whcap[id]=0;
        }
        if (allocBuffer(&whbufs[id], &whmems[id], &whmaps[id], (VkDeviceSize)m*k*4)) return -4;
        whcap[id] = (long)m*k;
    }
    if (wlcap[id] < (long)m*k){
        if (wlbufs[id]){
            vkUnmapMemory(dev, wlmems[id]);
            vkDestroyBuffer(dev, wlbufs[id], 0);
            vkFreeMemory(dev, wlmems[id], 0);
            wlbufs[id]=0; wlmems[id]=0; wlmaps[id]=0; wlcap[id]=0;
        }
        if (allocBuffer(&wlbufs[id], &wlmems[id], &wlmaps[id], (VkDeviceSize)m*k*4)) return -4;
        wlcap[id] = (long)m*k;
    }
    memcpy(whmaps[id], wh, (size_t)m*k*4);
    memcpy(wlmaps[id], wl, (size_t)m*k*4);
    return 0;
}

// M36.3: matvec split bit-exato — xh/xl computados no host (i32), y i64.
// Bindings: 0=wh[id] 1=wl[id] 2=xh 3=xl 4=y; push (m,k,divId,pad,div).
int vkchain64_wrunsp(int id, long* x, long* y, int m, int k, long long div){
    if (!inited) return -1;
    if (!pipeSplit || !pl5 || !dset5) return -6;  // SPV split não disponível
    if (id < 0 || id >= WMAX) return -2;
    if (!whbufs[id] || whcap[id] < (long)m*k || !wlbufs[id] || wlcap[id] < (long)m*k) return -3;
    if ((long)k > xhcap){
        if (xhbuf){ vkUnmapMemory(dev, xhmem); vkDestroyBuffer(dev, xhbuf, 0); vkFreeMemory(dev, xhmem, 0); }
        if (allocBuffer(&xhbuf, &xhmem, &xhmap, (VkDeviceSize)k*4)) return -4;
        xhcap = k;
    }
    if ((long)k > xlcap){
        if (xlbuf){ vkUnmapMemory(dev, xlmem); vkDestroyBuffer(dev, xlbuf, 0); vkFreeMemory(dev, xlmem, 0); }
        if (allocBuffer(&xlbuf, &xlmem, &xlmap, (VkDeviceSize)k*4)) return -4;
        xlcap = k;
    }
    if ((long)m > ycapElems){
        if (set_shape_internal(m, k)) return -4;
    }
    // xh/xl no host (o x i64: xh = x/1e6 <= 2e4, xl = x%1e6 — trunc, igual Kof)
    for (int i = 0; i < k; i++){
        ((int*)xhmap)[i] = (int)(x[i] / 1000000);
        ((int*)xlmap)[i] = (int)(x[i] % 1000000);
    }
    memset(ymap, 0, (size_t)m*8);
    VkDescriptorBufferInfo dbi[5];
    VkWriteDescriptorSet wds[5];
    dbi[0]=(VkDescriptorBufferInfo){whbufs[id],0,(VkDeviceSize)whcap[id]*4};
    dbi[1]=(VkDescriptorBufferInfo){wlbufs[id],0,(VkDeviceSize)wlcap[id]*4};
    dbi[2]=(VkDescriptorBufferInfo){xhbuf,0,(VkDeviceSize)xhcap*4};
    dbi[3]=(VkDescriptorBufferInfo){xlbuf,0,(VkDeviceSize)xlcap*4};
    dbi[4]=(VkDescriptorBufferInfo){ybuf,0,(VkDeviceSize)ycapElems*8};
    for (int i=0;i<5;i++)
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset5,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    vkUpdateDescriptorSets(dev,5,wds,0,0);

    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -5; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipeSplit);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl5,0,1,&dset5,0,0);
    int divId = (div == 1000000000LL) ? 0 : ((div == 1000000LL) ? 1 : 2);
    int pcs[6] = {m, k, divId, 0, 0, 0};
    memcpy(pcs+4, &div, 8);
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,24,pcs);
    vkCmdDispatch(cmd,(uint32_t)m,1,1);
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -5; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -5; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,60000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -5; }
    vkResetFences(dev,1,&fence);
    memcpy(y, ymap, (size_t)m*8);
    return 0;
}

int vkchain64_matvec(long* x, long* y, int m, int k){
    if (!inited) return -1;
    if (curM != m || curK != k) return -2;  // caller fez set_shape
    if (!wmap) return -3;
    // x → buffer mapeado (W JÁ está lá — o host escreveu direto via mapped_w)
    memcpy(xmap, x, (size_t)k*8);
    memset(ymap, 0, (size_t)m*8);

    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -4; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipe);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl,0,1,&dset,0,0);
    int pcs[2]={m,k};
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,8,pcs);
    vkCmdDispatch(cmd,(uint32_t)m,1,1);  // 1 workgroup = 1 row (64 threads)
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -4; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -4; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,60000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -4; }
    vkResetFences(dev,1,&fence);
    memcpy(y, ymap, (size_t)m*8);
    return 0;
}
