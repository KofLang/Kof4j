// vkchain.c — M32.3: cadeia Vulkan compute como biblioteca (RADV validado via C).
// Contrato:
//   int  vkchain_init(const char* spv_path)      → 0 ok / !=0 erro
//   const char* vkchain_fail_reason(void)
//   int  vkchain_dispatch(int* a, int* b, int* c, int m, int n, int k)
//        → 0 ok / !=0 (caller usa golden CPU)
// matmul.comp: 3 SSBOs (a,b,c) + push (M,N,K), local_size 16x16.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vulkan/vulkan.h>

static VkInstance inst; static VkDevice dev; static VkQueue q; static int qfam;
static VkPipeline pipe; static VkPipelineLayout pl;
static VkDescriptorPool dpool; static VkDescriptorSet dset;
static VkBuffer bufs[3]; static VkDeviceMemory mems[3]; static void* maps[3];
static VkCommandPool cpool; static VkCommandBuffer cmd;
static VkFence fence;
static char errbuf[256] = "not initialized";
static int inited = 0;

#define CK(x, msg) do { VkResult r=(x); if(r!=VK_SUCCESS){ snprintf(errbuf,sizeof errbuf,"%s (rc=%d)",msg,r); return 1; } } while(0)

const char* vkchain_fail_reason(void){ return errbuf; }

int vkchain_init(const char* spv_path){
    if (inited) return 0;
    VkApplicationInfo ai={VK_STRUCTURE_TYPE_APPLICATION_INFO,0,"kof",0,0,0,VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici={VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,0,0,&ai,0,0,0,0};
    CK(vkCreateInstance(&ici,0,&inst), "instance");
    uint32_t n=0;
    CK(vkEnumeratePhysicalDevices(inst,&n,0), "enum0");
    if (n==0){ snprintf(errbuf,sizeof errbuf,"nenhum physical device"); return 1; }
    VkPhysicalDevice phys[4];
    CK(vkEnumeratePhysicalDevices(inst,&n,phys), "enum");
    // escolher o primeiro com compute queue
    uint32_t qn=0; vkGetPhysicalDeviceQueueFamilyProperties(phys[0],&qn,0);
    VkQueueFamilyProperties qf[8]; vkGetPhysicalDeviceQueueFamilyProperties(phys[0],&qn,qf);
    qfam=0; while(qfam<(int)qn && !(qf[qfam].queueFlags&VK_QUEUE_COMPUTE_BIT)) qfam++;
    if (qfam>=(int)qn){ snprintf(errbuf,sizeof errbuf,"sem compute queue"); return 1; }
    float prio=1.0f;
    VkDeviceQueueCreateInfo qci={VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,0,0,(uint32_t)qfam,1,&prio};
    VkDeviceCreateInfo dci={VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,0,0,1,&qci,0,0,0,0};
    CK(vkCreateDevice(phys[0],&dci,0,&dev), "device");
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

    // 3 SSBOs
    VkDescriptorSetLayoutBinding binds[3];
    for (int b=0;b<3;b++) binds[b]=(VkDescriptorSetLayoutBinding){(uint32_t)b,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,1,VK_SHADER_STAGE_COMPUTE_BIT,0};
    VkDescriptorSetLayoutCreateInfo dli={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,0,0,3,binds};
    VkDescriptorSetLayout dsl;
    CK(vkCreateDescriptorSetLayout(dev,&dli,0,&dsl), "desc layout");
    VkPushConstantRange pcr={VK_SHADER_STAGE_COMPUTE_BIT,0,12};
    VkPipelineLayoutCreateInfo pli={VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,0,0,1,&dsl,1,&pcr};
    CK(vkCreatePipelineLayout(dev,&pli,0,&pl), "pipe layout");
    VkPipelineShaderStageCreateInfo stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,0,0,VK_SHADER_STAGE_COMPUTE_BIT,sm,"main",0};
    VkComputePipelineCreateInfo pci={VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,0,0,stage,pl,0,0};
    CK(vkCreateComputePipelines(dev,0,1,&pci,0,&pipe), "pipeline");

    // 3 buffers + mem host-visible + map (256B cada = 64 ints máx por Matmul; redimensionável)
    VkPhysicalDeviceMemoryProperties mp;
    vkGetPhysicalDeviceMemoryProperties(phys[0],&mp);
    for (int i=0;i<3;i++){
        VkBufferCreateInfo bci={VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,0,0,256*4,VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,VK_SHARING_MODE_EXCLUSIVE,0,0};
        CK(vkCreateBuffer(dev,&bci,0,&bufs[i]), "buffer");
        VkMemoryRequirements req; vkGetBufferMemoryRequirements(dev,bufs[i],&req);
        int mi=-1;
        for (uint32_t t=0;t<mp.memoryTypeCount;t++)
            if ((req.memoryTypeBits&(1u<<t))&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)&&(mp.memoryTypes[t].propertyFlags&VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){mi=(int)t;break;}
        if (mi<0){ snprintf(errbuf,sizeof errbuf,"sem mem host-visible"); return 1; }
        VkMemoryAllocateInfo mai={VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,0,req.size,(uint32_t)mi};
        CK(vkAllocateMemory(dev,&mai,0,&mems[i]), "alloc mem");
        CK(vkBindBufferMemory(dev,bufs[i],mems[i],0), "bind mem");
        CK(vkMapMemory(dev,mems[i],0,256*4,0,&maps[i]), "map");
    }

    // desc pool + set
    VkDescriptorPoolSize ps={VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,3};
    VkDescriptorPoolCreateInfo dpi={VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,0,0,1,1,&ps};
    CK(vkCreateDescriptorPool(dev,&dpi,0,&dpool), "desc pool");
    VkDescriptorSetAllocateInfo dsai={VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,0,dpool,1,&dsl};
    CK(vkAllocateDescriptorSets(dev,&dsai,&dset), "desc set");
    VkDescriptorBufferInfo dbi[3];
    VkWriteDescriptorSet wds[3];
    for (int i=0;i<3;i++){
        dbi[i]=(VkDescriptorBufferInfo){bufs[i],0,256*4};
        wds[i]=(VkWriteDescriptorSet){VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,0,dset,(uint32_t)i,0,1,VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,0,&dbi[i],0};
    }
    vkUpdateDescriptorSets(dev,3,wds,0,0);

    // cmd pool/buffer
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

int vkchain_dispatch(int* a, int* b, int* c, int m, int n, int k){
    if (!inited) return -1;
    if ((size_t)m*k > 256 || (size_t)k*n > 256 || (size_t)m*n > 256) return -2; // >64x64: não cabe
    memcpy(maps[0], a, (size_t)m*k*4);
    memcpy(maps[1], b, (size_t)k*n*4);
    memset(maps[2], 0, (size_t)m*n*4);

    CK2: ;
    VkCommandBufferBeginInfo bbi={VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,0,VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,0};
    VkResult r = vkBeginCommandBuffer(cmd,&bbi);
    if (r) { snprintf(errbuf,sizeof errbuf,"begin rc=%d",r); return -3; }
    vkCmdBindPipeline(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pipe);
    vkCmdBindDescriptorSets(cmd,VK_PIPELINE_BIND_POINT_COMPUTE,pl,0,1,&dset,0,0);
    int pcs[3]={m,n,k};
    vkCmdPushConstants(cmd,pl,VK_SHADER_STAGE_COMPUTE_BIT,0,12,pcs);
    uint32_t gx=(n+15)/16, gy=(m+15)/16;
    vkCmdDispatch(cmd,gx,gy,1);
    r = vkEndCommandBuffer(cmd);
    if (r) { snprintf(errbuf,sizeof errbuf,"end rc=%d",r); return -3; }
    VkSubmitInfo si={VK_STRUCTURE_TYPE_SUBMIT_INFO,0,0,0,0,1,&cmd,0,0};
    r = vkQueueSubmit(q,1,&si,fence);
    if (r) { snprintf(errbuf,sizeof errbuf,"submit rc=%d",r); return -3; }
    r = vkWaitForFences(dev,1,&fence,VK_TRUE,5000000000ull);
    if (r) { snprintf(errbuf,sizeof errbuf,"wait rc=%d",r); return -3; }
    vkResetFences(dev,1,&fence);
    memcpy(c, maps[2], (size_t)m*n*4);
    return 0;
}
