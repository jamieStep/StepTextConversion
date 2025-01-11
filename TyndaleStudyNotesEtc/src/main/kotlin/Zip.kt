/******************************************************************************/
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
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
   *   Take care to ensure this genuinely is a prefix of all the individual
   *   input files.  May be null, in which case each element is taken
   *   relative to its own parent.
   *
   * @param inputs Input files or folders.  Each may optionally be terminated
   *   by "¬" followed by something starting S(tored) or
   *   D(eflated) to determine the compression to be applied.
   *   If this is not supplied, Deflated is assumed.
   */

  fun createZipFile (zipFilePath: String,
                     compressionLevel: Int,
                     theRelativeTo: String?,
                     inputs: List<String>)
  {
     /*************************************************************************/
     if (inputs.isEmpty())
       return



     /*************************************************************************/
     /* Sort out relative paths etc. */

     val relativeTo = if (null == theRelativeTo) null else File(theRelativeTo).canonicalPath
     fun dealWithRelativePath (asSupplied: String): Triple<String, String, Int>
     {
       val bits = asSupplied.split("¬") // May have ¬ followed by compression type.

       val compressionType =
         if (1 == bits.size)
           ZipOutputStream.DEFLATED // Default.
         else
         {
           if ("d" == bits[1].lowercase()) ZipOutputStream.DEFLATED else ZipOutputStream.STORED
         }

       val f = File(bits[0])
       val canonicalPath = f.canonicalPath
       val canonicalParent = f.parent
       val canonicalSelf = f.name

       return if (null == relativeTo) // If no overarching relativeTo, then make the thing relative to its own parent.
         Triple(canonicalParent, canonicalSelf, compressionType)
       else
       {
         if (canonicalPath.startsWith(relativeTo))
           Triple(relativeTo, canonicalPath.substring(relativeTo.length + 1), compressionType)  // The thing is in the path starting relativeTo, so we can give it as relative.
         else
           throw StepExceptionWithStackTraceAbandonRun("File to be zipped not in relativeTo path: $asSupplied.") // Triple(null, canonicalPath, compressionType) // Not in the relativeTo path.  I have a feeling perhaps this should be an exception.
       }
    }

    val myInputs = inputs.map { dealWithRelativePath(it) }



    /*************************************************************************/
    try
    {
      FileOutputStream(zipFilePath).use { dest ->
        m_ZipStream = ZipOutputStream(BufferedOutputStream(dest))
        if (compressionLevel >= 0) m_ZipStream!!.setLevel(compressionLevel)
        myInputs.forEach { zip(it.first, it.second, it.third)}
        m_ZipStream!!.close()
      } // use
    }
    catch (e: Exception)
    {
      throw StepExceptionWithStackTraceAbandonRun("Zip failed: ${e}.")
    }
  }


  /****************************************************************************/
  /**
   * Reader: returns a zip entry for a given file within a zip, or null if
   * not found.
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
   * Reader: returns an input stream for a given member of a zip file, or null
   * if the entry is not found.
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
   * Reader: returns a buffered reader for a given member of a zip file, or
   * null if the entry is not found.
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
  /* Processes a single folder or file. */

  private fun zip (relativeTo: String, path: String, method: Int)
  {
    val absolutePath = Paths.get(relativeTo, path).toString()
    val f = File(absolutePath)
    if (f.isFile)
      zipFile(path, absolutePath, method)
    else
      f.list().forEach { zip(relativeTo, absolutePath.substring(1 + relativeTo.length) + File.separator + it, method) }
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