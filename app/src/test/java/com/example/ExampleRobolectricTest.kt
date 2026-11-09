package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatMessage
import com.example.engine.LocalDeviceAiEngine
import com.example.engine.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AI Server", appName)
  }

  @Test
  fun `verify model catalog has default models`() {
    val models = ModelCatalog.defaultModels
    assertTrue(models.isNotEmpty())
    assertNotNull(models.find { it.id == "local-device-ai" })
  }

  @Test
  fun `verify local device engine generates valid response`() {
    val messages = listOf(ChatMessage("user", "Hello"))
    val reply = LocalDeviceAiEngine.generate(messages, "local-device-ai")
    assertTrue(reply.isNotBlank())
  }
}
