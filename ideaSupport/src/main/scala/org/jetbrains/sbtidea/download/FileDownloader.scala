package org.jetbrains.sbtidea.download

import java.io.FileOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{Files, Path, Paths}

import org.jetbrains.sbtidea.PluginLogger
import org.jetbrains.sbtidea._

private class FileDownloader(private val baseDirectory: Path, log: PluginLogger) {

  type ProgressCallback = (ProgressInfo, Path) => Unit

  private case class RemoteMetaData(length: Long, fileName: String)
  class DownloadException(message: String) extends RuntimeException(message)

  private val downloadDirectory = getOrCreateDLDir()

  def download(artifactPart: ArtifactPart): Path = try {
    val partFile = downloadNative(artifactPart.url) { case (progressInfo, to) =>
        val text = s"${progressInfo.renderAll} -> $to\r"
        if (!progressInfo.done) print(text) else println(text)
    }
    val targetFile = partFile.getParent.resolve(partFile.getFileName.toString.replace(".part", ""))
    Files.move(partFile, targetFile)
    targetFile
  } catch {
    case e: Exception if artifactPart.optional =>
      log.warn(s"Can't download optional ${artifactPart.url}: $e")
      Paths.get("")
  }

  // TODO: add downloading to temp if available
  private def getOrCreateDLDir(): Path = {
    val dir = baseDirectory.resolve("downloads")
    if (!dir.toFile.exists())
      Files.createDirectories(dir)
    dir
  }

  case class ProgressInfo(percent: Int, speed: Double, downloaded: Long, total: Long) {
    def renderBar: String = {
      val width = jline.TerminalFactory.get().getWidth / 4 // quarter width for a progressbar is fine
      s"[${"=" * ((percent * width / 100) - 1)}>${"." * (width-(percent * width / 100))}]"
    }

    def renderSpeed: String = {
      if (speed < 1024)                     "%.0f B/s".format(speed)
      else if (speed < 1024 * 1024)         "%.2f KB/s".format(speed / 1024.0)
      else if (speed < 1024 * 1024 * 1024)  "%.2f MB/s".format(speed / (1024.0 * 1024.0))
      else                                  "%.2f GB/s".format(speed / (1024.0 * 1024.0 * 1024.0))
    }

    private def space = if (percent == 100) "" else " "

    def renderText: String = s"$renderSpeed; ${(downloaded / (1024 * 1024)).toInt}/${(total / (1024 * 1024)).toInt}MB"

    def renderAll: String = s"$percent%$space $renderBar @ $renderText"

    def done: Boolean = downloaded == total
  }

  private def downloadNative(url: URL)(progressCallback: ProgressCallback): Path = {
    val connection = url.openConnection()
    val remoteMetaData = getRemoteMetaData(url)
    val to =
      if (remoteMetaData.fileName.nonEmpty)
        downloadDirectory.resolve(remoteMetaData.fileName + ".part")
      else
        downloadDirectory.resolve(Math.abs(url.hashCode()).toString + ".part")
    val localLength = if (to.toFile.exists()) Files.size(to) else 0
    if (to.toFile.exists() && isResumeSupported(url)) {
      connection.setRequestProperty("Range", s"bytes=$localLength-")
      log.info(s"Resuming download of $url to $to")
    } else {
      Files.deleteIfExists(to)
      log.info(s"Starting download $url to $to")
    }

    var inChannel: ReadableByteChannel = null
    var outStream: FileOutputStream    = null
    try {
      inChannel = Channels.newChannel(connection.getInputStream)
      outStream = new FileOutputStream(to.toFile, to.toFile.exists())
      val rbc   = new RBCWrapper(inChannel, remoteMetaData.length, localLength, progressCallback, to)
      outStream.getChannel.transferFrom(rbc, 0, Long.MaxValue)
      to
    } finally {
      try { if (inChannel != null) inChannel.close() } catch { case e: Exception => log.error(s"Failed to close input channel: $e") }
      try { if (outStream != null) outStream.close() } catch { case e: Exception => log.error(s"Failed to close output stream: $e") }
    }
  }

  private def isResumeSupported(url: URL): Boolean = withConnection(url) { connection =>
    try   { connection.getResponseCode != 206 }
    catch { case e: Exception => log.warn(s"Error checking for a resumed download: ${e.getMessage}"); false }
  }

  private def getRemoteMetaData(url: URL): RemoteMetaData = withConnection(url) { connection =>
    connection.setRequestMethod("HEAD")
    if (connection.getResponseCode >= 400)
      throw new DownloadException(s"Not found (404): $url")
    val contentLength = connection.getContentLength
    val nameFromHeader = java.net.URLDecoder
      .decode(
        connection
          .getHeaderField("Content-Disposition")
          .lift2Option
          .map(_.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1"))
          .getOrElse(""),
        "ISO-8859-1")
    val nameFromURL = url.toString.split("/").lastOption.getOrElse("")
    val name =
      if (nameFromHeader.nonEmpty)
        nameFromHeader
      else if (nameFromURL.isValidFileName)
        nameFromURL
      else
        Math.abs(url.hashCode()).toString
    RemoteMetaData(if (contentLength != 0) contentLength else -1, name)
  }


  class RBCWrapper(rbc: ReadableByteChannel, expectedSize: Long, alreadyDownloaded: Long, progressCallback: ProgressCallback, target: Path) extends ReadableByteChannel {
    private var readSoFar       = alreadyDownloaded
    private var lastTimeStamp   = System.currentTimeMillis()
    private var readLastSecond  = 0L
    override def isOpen: Boolean  = rbc.isOpen
    override def close(): Unit    = rbc.close()
    override def read(bb: ByteBuffer): Int = {
      var numRead = rbc.read(bb)
      if (numRead > 0) {
        readSoFar       += numRead
        readLastSecond  += numRead
        val newTimeStamp = System.currentTimeMillis()
        if (newTimeStamp - lastTimeStamp >= 1000 || readSoFar == expectedSize) { // update every second or on finish
          val percent = if (expectedSize > 0)
            readSoFar.toDouble / expectedSize.toDouble * 100.0
          else
            -1.0
          val speed = readLastSecond.toDouble / ((newTimeStamp - lastTimeStamp + 1) / 1000.0)
          progressCallback(ProgressInfo(percent.toInt, speed, readSoFar, expectedSize), target)
          lastTimeStamp  = newTimeStamp
          readLastSecond = 0
        }
      }
      numRead
    }
  }

}
