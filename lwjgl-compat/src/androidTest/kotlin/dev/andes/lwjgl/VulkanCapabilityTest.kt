package dev.andes.lwjgl

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VulkanCapabilityTest {
    @Test fun recordsActualSelectedVulkanFeaturesWithoutClaimingRendererCompatibility() {
        val raw = VulkanProbe.inspect()
        assertEquals(0, raw.size % 7)
        val devices = JSONArray()
        val features = listOf("logicOp", "fillModeNonSolid", "alphaToOne", "shaderClipDistance", "independentBlend", "depthClamp", "geometryShader", "shaderTessellationAndGeometryPointSize")
        val extensions = listOf("VK_EXT_transform_feedback", "VK_EXT_conditional_rendering", "VK_EXT_depth_clip_enable", "VK_EXT_robustness2", "VK_KHR_robustness2", "VK_EXT_custom_border_color", "VK_EXT_provoking_vertex")
        for (i in raw.indices step 7) {
            val version = raw[i]
            assertTrue(version shr 22 >= 1)
            val f = JSONObject(); features.forEachIndexed { index, name -> f.put(name, raw[i+1] and (1L shl index) != 0L) }
            val e = JSONObject(); extensions.forEachIndexed { index, name -> e.put(name, raw[i+4] and (1L shl index) != 0L) }
            devices.put(JSONObject().put("apiVersion", "${version shr 22}.${(version shr 12) and 1023}.${version and 4095}")
                .put("coreFeatures", f).put("selectedExtensions", e).put("maxPerStageDescriptorSamplers", raw[i+2])
                .put("maxPerStageDescriptorUniformBuffers", raw[i+3]).put("vendorId", raw[i+5]).put("deviceType", raw[i+6]))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "vulkan-capabilities.json").writeText(JSONObject().put("devices", devices)
            .put("limitation", "Selected core flags and extension presence only; extension feature structures, formats, complete Zink profile and actual rendering remain untested.")
            .put("desktopGlValidated", false).toString(2))
    }
}
