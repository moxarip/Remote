package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.StorageUtils
import org.junit.Assert.assertEquals
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
    assertEquals("Remote Backup", appName)
  }

  @Test
  fun `test storage formatting`() {
    assertEquals("0 B", StorageUtils.formatFileSize(0L))
    assertEquals("1 KB", StorageUtils.formatFileSize(1024L))
    assertEquals("1 MB", StorageUtils.formatFileSize(1024L * 1024L))
    assertTrue(StorageUtils.formatFileSize(34_600_000_000L).contains("GB"))
  }

  @Test
  fun `test file category classification`() {
    assertEquals("Photos", StorageUtils.getCategoryForFile("vacation.jpg", "image/jpeg"))
    assertEquals("Videos", StorageUtils.getCategoryForFile("movie.mp4", "video/mp4"))
    assertEquals("Documents", StorageUtils.getCategoryForFile("report.pdf", "application/pdf"))
    assertEquals("Other", StorageUtils.getCategoryForFile("archive.bin", "application/octet-stream"))
  }

  @Test
  fun `test device role values`() {
    assertEquals("HOST", com.example.models.DeviceRole.HOST.name)
    assertEquals("ADMIN", com.example.models.DeviceRole.ADMIN.name)
  }
}
