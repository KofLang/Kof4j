/* vkboot.c — M36.5: bootstrap Vulkan mínimo (instance + phys[0] +
 * device de compute + queue) em C, porque o vkCreateDevice via FFM
 * (JDK 25.0.3 + Mesa RADV/LVP) produz um device cujos SSBOs ficam
 * invisíveis ao GPU de forma não-determinística (probes 18-23).
 * Toda a cadeia (buffers, pipelines, descritores, record, submit2)
 * permanece 100% FFM no JvmVkRuntime.
 * Exporta: int vkboot(long out[5]) → {instance, phys, device,
 * queue, qfam}. Retorna 0 ok / -1 falha (mensagem em stderr). */
#include <vulkan/vulkan.h>
#include <stdint.h>
#include <stdio.h>

int vkboot(long* out) {
    VkApplicationInfo ai = {VK_STRUCTURE_TYPE_APPLICATION_INFO, 0,
                            "kof", 0, 0, 0, VK_API_VERSION_1_3};
    VkInstanceCreateInfo ici = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
                                0, 0, &ai, 0, 0, 0, 0};
    VkInstance inst;
    if (vkCreateInstance(&ici, 0, &inst) != VK_SUCCESS) {
        fprintf(stderr, "[vkboot] vkCreateInstance falhou\n");
        return -1;
    }
    uint32_t n = 0;
    vkEnumeratePhysicalDevices(inst, &n, 0);
    if (n == 0) {
        fprintf(stderr, "[vkboot] sem physical device\n");
        return -1;
    }
    VkPhysicalDevice pd[4];
    if (n > 4) n = 4;
    vkEnumeratePhysicalDevices(inst, &n, pd);
    uint32_t qn = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(pd[0], &qn, 0);
    VkQueueFamilyProperties qf[16];
    if (qn > 16) qn = 16;
    vkGetPhysicalDeviceQueueFamilyProperties(pd[0], &qn, qf);
    int qfam = -1;
    for (uint32_t i = 0; i < qn; i++) {
        if (qf[i].queueFlags & VK_QUEUE_COMPUTE_BIT) { qfam = (int)i; break; }
    }
    if (qfam < 0) {
        fprintf(stderr, "[vkboot] sem compute queue\n");
        return -1;
    }
    float prio = 1.0f;
    VkDeviceQueueCreateInfo qci = {VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                                   0, 0, (uint32_t)qfam, 1, &prio};
    VkDeviceCreateInfo dci = {VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
                              0, 0, 1, &qci, 0, 0, 0, 0};
    VkDevice dev;
    if (vkCreateDevice(pd[0], &dci, 0, &dev) != VK_SUCCESS) {
        fprintf(stderr, "[vkboot] vkCreateDevice falhou\n");
        return -1;
    }
    VkQueue queue;
    vkGetDeviceQueue(dev, (uint32_t)qfam, 0, &queue);
    out[0] = (long)(uintptr_t)inst;
    out[1] = (long)(uintptr_t)pd[0];
    out[2] = (long)(uintptr_t)dev;
    out[3] = (long)(uintptr_t)queue;
    out[4] = (long)qfam;
    return 0;
}
