/******************************************************************************/
package org.stepbible.textconverter.support.miscellaneous

import org.stepbible.textconverter.support.stepexception.StepException
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.*


/******************************************************************************/
/**
 * A ZIP file handler.
 *
 *  @author ARA "Jamie" Jamieson
 */

object Zip
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Compresses a string.  The result is returned as a byte array.
   *
   * Caution: Particularly where the string to be compressed is small, it is
   * perfectly possible that the 'compressed' form may be *larger* than
   * the original.
   *
   * @param buf Data to be compressed.
   * @param offset Offset into buf at which data for compression starts.
   * @param len Length of data for compression.
   * @param compressionLevel Does what it says on the tin. Use -1 for the default setting.
   * @param gZipFormat True if you want to use GZIP format.
   * @return Compressed string.
   */

  fun compressByteBufferToByteArray (buf: ByteArray, offset: Int, len: Int, compressionLevel: Int, gZipFormat: Boolean): ByteArray
  {
    val compressor = Deflater(compressionLevel, gZipFormat)
    compressor.setInput(buf, offset, len)
    compressor.finish()
    val bao = ByteArrayOutputStream()
    val readBuffer = ByteArray(1024)
    var readCount: Int

    while (!compressor.finished())
    {
      readCount = compressor.deflate(readBuffer)
      if (readCount > 0) bao.write(readBuffer, 0, readCount)
    }
    compressor.end()

    return bao.toByteArray()
  }


  /****************************************************************************/
  /**
   * Uses GZIP to compress a string.  The result is returned as a byte array
   * representation encoded using ISO_8859_1.
   *
   * Caution: Particularly where the string to be compressed is small, it is
   * perfectly possible that the 'compressed' form may be *larger* than
   * the original.
   *
   * @param s String to be compressed.
   * @return Compressed string.
   */

  fun compressStringToByteArray (s: String): ByteArray
  {
    return compressStringToByteArray(s, StandardCharsets.ISO_8859_1)
  }


  /****************************************************************************/
  /**
   * Uses GZIP to compress a string.  The result is returned as a byte array
   * representation encoded using the specified encoding.
   *
   * Caution: Particularly where the string to be compressed is small, it is
   * perfectly possible that the 'compressed' form may be *larger* than
   * the original.
   *
   * @param s String to be compressed.
   * @param charset The encoding to be used.
   * @return Compressed string.
   */

  fun compressStringToByteArray (s: String, charset: Charset): ByteArray
  {
    val bos = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(bos)
    OutputStreamWriter(gzip, charset).use { osw ->
      osw.write(s)
      osw.close()
    }
    return bos.toByteArray()
  }


  /****************************************************************************/
  /**
   * Uses GZIP to compress a string.  The result is returned as a string
   * representation encoded using ISO_8859_1.
   *
   * Caution: Particularly where the string to be compressed is small, it is
   * perfectly possible that the 'compressed' form may be *larger* than
   * the original.
   *
   * @param s String to be compressed.
   * @return Compressed string.
   */

  fun compressStringToString (s: String): String
  {
    return compressStringToString(s, StandardCharsets.ISO_8859_1)
  }


  /****************************************************************************/
  /**
   * Uses GZIP to compress a string.  The result is returned as a string
   * representation encoded using the specified encoding.
   *
   * Caution: Particularly where the string to be compressed is small, it is
   * perfectly possible that the 'compressed' form may be *larger* than
   * the original.
   *
   * @param s String to be compressed.
   * @param charset The encoding to be used.
   * @return Compressed string.
   */

  fun compressStringToString (s: String, charset: Charset): String
  {
    val bos = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(bos)
    OutputStreamWriter(gzip, charset).use { osw ->
      osw.write(s)
      osw.close()
    }

    return bos.toByteArray().toString(charset)
  }


  /****************************************************************************/
  /**
   * Decompresses a byte array
   *
   * @param input Data to be decompressed.
   * @param offset Offset into data area where compression is to start.
   * @param theLength Number of bytes (if 0, the input of the array is assumed).
   * @param gZipFormat True if you want to use GZIP format.
   * @return Decompressed data.
   * @throws IOException Any IO error.
   */

  fun decompressByteArrayToByteArray (input: ByteArray, offset: Int, theLength: Int, gZipFormat: Boolean): ByteArray

  {
    var length = theLength
    val decompressor = Inflater(gZipFormat)
    if (0 == length) length = input.size
    decompressor.setInput(input, offset, length)
    val bao = ByteArrayOutputStream()
    val readBuffer = ByteArray(1024)
    var readCount: Int

    while (!decompressor.finished())
    {
      readCount = decompressor.inflate(readBuffer)
      if (readCount > 0) bao.write(readBuffer, 0, readCount)
    }
    decompressor.end()

    return bao.toByteArray()
  }


  /****************************************************************************/
  /**
   * Decompresses a string which was compressed using compressString, qv.
   * The string must have been compressed using ISO_8859_1.
   *
   * @param s String to be decompressed.
   * @return Decompressed string.
   * @throws Exception Decompression error.
   */

  @JvmOverloads
  fun decompressString (s: String, charset: Charset = StandardCharsets.ISO_8859_1): String {
    val isr = InputStreamReader(GZIPInputStream(ByteArrayInputStream(s.toByteArray())), charset)
    val sw = StringWriter()
    val chars = CharArray(1024)
    var len: Int
    while (isr.read(chars).also { len = it } > 0)
    {
      sw.write(chars, 0, len)
    }
    return sw.toString()
  }


  /****************************************************************************/
  /**
   * Zips one or more files or folders, retaining structure information.
   *
   * @param zipFilePath Path for output zip file.
   *
   * @param compressionLevel Compression level.  If negative, the inbuilt
   *   default is used.
   *
   * @param theRelativeTo Path relative to which relative paths are determined.
   *   Take care to ensure this genuinely is a prefix of all
   *   the individual input files.
   *
   * @param inputs Input files or folders.  Each may optionally be terminated
   *   by "¬" followed by something starting S(tored) or
   *   D(eflated) to determine the compression to be applied.
   *   If this is not supplied, Deflated is assumed.
   */

  fun createZipFile (zipFilePath: String,
                     compressionLevel: Int,
                     theRelativeTo: String,
                     inputs: MutableList<String>)
  {
    var relativeTo = theRelativeTo
    try
    {
      FileOutputStream(zipFilePath).use { dest ->
        m_ZipStream = ZipOutputStream(BufferedOutputStream(dest))
        if (compressionLevel >= 0) m_ZipStream!!.setLevel(compressionLevel)
        if (relativeTo.isNotEmpty()) relativeTo = File(relativeTo).canonicalPath
        if (relativeTo.isNotEmpty())
          for (i in inputs.indices)
          {
            val s = File(inputs[i]).canonicalPath
            if (s.startsWith(relativeTo)) inputs[i] = s.substring(relativeTo.length + 1) // Convert to relative form if necessary, removing the leading "\" also.
          }

        for (i in inputs.indices) zip(relativeTo, inputs[i])
        m_ZipStream!!.close()
      } // use
    }
    catch (e: Exception)
    {
      throw StepException("Zip failed")
    }
  }


  /****************************************************************************/
  /**
   * Returns a zip entry for a given file within a zip, or null if not found.
   *
   * @param zipFileName
   * @param memberFileName
   * @return Pair comprising the zip file itself and the entry.
   * The caller must close the zip file when it is no longer required.
   */

  fun getZipEntry (zipFileName: String, memberFileName: String): Pair<ZipEntry, ZipFile>?
  {
    ZipFile(zipFileName).use { zipFile ->
      val entries = zipFile.entries()
      while (entries.hasMoreElements())
      {
        val entry = entries.nextElement()
        if (entry.name == memberFileName) return Pair(entry, zipFile)
      }
    }

    return null
  }


  /****************************************************************************/
  /**
   * Returns an input stream for a given member of a zip file, or null if
   * the entry is not found.
   *
   * @param zipFileName
   * @param memberFileName What it says on the tin.
   * @return Pair comprising the zip file itself and the input stream.
   * The caller must close both when they are no longer required.
   */

  fun getInputStream (zipFileName: String, memberFileName: String): Pair<InputStream, ZipFile>?
  {
    var res: InputStream? = null
    val zipFile = ZipFile(zipFileName)
    val entries = zipFile.entries()
    while (entries.hasMoreElements())
    {
      val entry = entries.nextElement()
      if (entry.name == memberFileName)
      {
        res = zipFile.getInputStream(entry)
        break
      }
    }

    return if (null == res) null else Pair(res, zipFile)
  }


  /****************************************************************************/
  /**
   * Returns a buffered reader for a given member of a zip file, or null if
   * the entry is not found.
   *
   * @param zipFileName
   * @param memberFileName
   * @return Buffered reader and zip file.
   */

  fun getRandomAccessFile (zipFileName: String, memberFileName: String): Pair<InputStream, ZipFile>?
  {
    var res: InputStream? = null
    val zipFile = ZipFile(zipFileName)
    val entries = zipFile.entries()
    while (entries.hasMoreElements())
    {
      val entry = entries.nextElement()
      if (entry.name == memberFileName)
      {
        res = zipFile.getInputStream(entry)
        break
      }
    }

    return if (null == res) null else Pair(res, zipFile)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private var m_ZipStream: ZipOutputStream? = null

  /****************************************************************************/
  /* Processes a single input -- splits out and applies any compression
     setting, and then passes the input on for further processing. */

  private fun zip (relativeTo: String, path: String)
  {
    val details = path.split("¬".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val method: Int = if (2 == details.size) if (details[1].uppercase().startsWith("S")) ZipOutputStream.STORED else ZipOutputStream.DEFLATED else ZipOutputStream.DEFLATED
    if (details[0].isNotEmpty()) zip1(relativeTo, details[0], method)
  }


  /****************************************************************************/
  /* Processes a single folder or file. */

  private fun zip1 (relativeTo: String, path: String, method: Int)
  {
    val absolutePath = (if (relativeTo.isEmpty()) "" else relativeTo + File.separator) + path
    if (File(absolutePath).isFile)
      zipFile(path, absolutePath, method)
    else
    {
      val f = File(absolutePath)
      val files = f.list()
      for (i in files!!.indices)
        zip1(relativeTo, absolutePath.substring(1 + relativeTo.length) + File.separator + files[i], method)
    }
  }


  /****************************************************************************/
  /* Processes a single file.  Note that when I set up the ZipEntry structure,
     I force the relative path to use "/" as a folder separator, rather than
     "\".  I'm not sure whether this is always necessarily the correct thing
     to do, but if you're using the stuff here to produce ePub files, the
     Calibre reader would only read a file once before it started complaining
     about mismatches between "/" and "\". */

  private fun zipFile (relativePath: String, absolutePath: String, method: Int)
  {
    val C_BufLen = 65536
    var count: Int
    val data = ByteArray(C_BufLen)
    val entry = ZipEntry(relativePath.replace(File.separator, "/"))
    entry.method = method
    if (ZipOutputStream.STORED == method)
    {
      entry.size = File(absolutePath).length()
      val crc = CRC32()
      FileInputStream(absolutePath).use { fi -> BufferedInputStream(fi, C_BufLen).use { source -> while (source.read(data, 0, C_BufLen).also { count = it } != -1) crc.update(data, 0, count) } }
      entry.crc = crc.value
    }

    m_ZipStream!!.putNextEntry(entry)
    FileInputStream(absolutePath).use { fi -> BufferedInputStream(fi, C_BufLen).use { source -> while (source.read(data, 0, C_BufLen).also { count = it } != -1) m_ZipStream!!.write(data, 0, count) } }
  }
}