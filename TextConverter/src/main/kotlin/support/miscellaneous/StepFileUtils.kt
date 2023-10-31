/******************************************************************************/
package org.stepbible.textconverter.support.miscellaneous

import org.apache.commons.io.FilenameUtils
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths



/******************************************************************************/
/**
 * Miscellaneous file-related utilities.
 *
 * @author ARA "Jamie" Jamieson
 */

object StepFileUtils
{
  /******************************************************************************/
  /**
   * Deletes all files in a given folder.
   *
   * @param folderPath Path to folder.
   */

  fun clearFolder (folderPath: String)
  {
    File(folderPath).listFiles()?.forEach { it.delete() }
  }


  /******************************************************************************/
  /**
   * Copies a file.
   *
   * @param toPath Target path name.
   * @param fromPath Source path name.
   */

  fun copyFile (toPath: String, fromPath: String)
  {
    Files.copy(Paths.get(fromPath), Paths.get(toPath))
  }


  /******************************************************************************/
  /**
   * Creates an entire folder structure -- the target folder itself, plus any
   * folders above it which also need to be created.
   *
   * @param folderPath Full path name of folder to be created.
   */

  fun createFolderStructure (folderPath: String)
  {
    File(folderPath).mkdirs()
  }


  /******************************************************************************/
  /**
   * Deletes a file without complaining if it doesn't exist.
   *
   * @param p File to be deleted.
   */

  fun deleteFile (p: Path)
  {
    p.toFile().delete()
  }


  /******************************************************************************/
  /**
   * Deletes a file without complaining if it doesn't exist.
   *
   * @param f File to be deleted.
   */

  fun deleteFile (f: File)
  {
    f.delete()
  }


  /******************************************************************************/
  /**
   * Deletes a file without complaining if it doesn't exist.
   *
   * @param f Path name of file to be deleted.
   */

  fun deleteFile (f: String)
  {
    File(f).delete()
  }


  /******************************************************************************/
  /**
   * Deletes a folder even if it contains file.
   *
   * @param path Folder path.
   */

  fun deleteFileOrFolder (path: String)
  {
    try
    {
      File(path).deleteRecursively()
    }
    catch (_: Exception)
    {
    }
  }


  /****************************************************************************/
  /**
   * Deletes a folder even if it contains file.
   *
   * @param path Folder path.
   */

  fun deleteFolder (path: String)
  {
    try
    {
      File(path).deleteRecursively()
    }
    catch (_: Exception)
    {
    }
  }


  /******************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param pathName Path to be checked.
   * @return True if file exists.
   */

  fun fileExists (pathName: String): Boolean
  {
    return File(pathName).exists()
  }


  /******************************************************************************/
  /**
   * Does what it says on the tin.
   *
   * @param folderName Folder to be scanned.
   * @return Size of folder.
   */

  fun folderSize (folderName: String): Long
  {
    return File(folderName).walkTopDown().filter { it.isFile }.map { it.length() }.sum()
  }


  /*****************************************************************************/
  /**
   * Returns the extension of a file.
   *
   * @param fileName File name.
   * @return  Extension, or empty string.
   */

  fun getFileExtension (fileName: String): String
  {
    return File(fileName).extension
  }


  /******************************************************************************/
  /**
   * Returns the size of a file.
   *
   * @param filePath File name.
   * @return Size of file or -1 if the file does not exist.
   */

  fun getFileSize (filePath: String): Long
  {
    if (!fileExists(filePath)) return -1
    return File(filePath).length()
  }


  /******************************************************************************/
  /**
   * Returns the filename portion of a path.
   *
   * @param path Full path.
   *
   * @return Filename.
   */

  fun getFileName (path: Path): String
  {
    return path.fileName.toString()
  }


  /******************************************************************************/
  /**
   * Returns the filename portion of a path.
   *
   * @param path Full path name.
   *
   * @return Filename.
   */

  fun getFileName (path: String): String
  {
    return Paths.get(path).fileName.toString()
  }


  /******************************************************************************/
  /** Gets Path entries for all the files in a given folder whose names match a
   *  given pattern.
   *
   * @param folderName Name of folder to be scanned.
   * @param pat Pattern-match name.
   *
   * @return List of matching file details.
   */

  fun getMatchingFilesFromFolder (folderName: String, pat: Regex): List<Path>
  {
    return getMatchingThingsFromFolder(folderName, pat, "F")
  }


  /******************************************************************************/
  /** Gets Path entries for all the folders in a given folder whose names match
   *  a given pattern.
   *
   * @param folderName Name of folder to be scanned.
   * @param pat Pattern-match name.
   *
   * @return List of matching file details.
   */

  fun getMatchingFoldersFromFolder (folderName: String, pat: Regex): List<Path>
  {
    return getMatchingThingsFromFolder(folderName, pat, "D")
  }


  /******************************************************************************/
  /**
   * Returns the name of the parent folder for a given file or folder.
   *
   * @param pathName File or folder whose parent is required.
   * @return Name of parent folder.
   */

  fun getParentFolderName (pathName: String): String
  {
    return File(pathName).parent
  }


  /******************************************************************************/
  /** Looks for a single file in a folder matching a given pattern.
   *
   * @param folderName Name of folder to be scanned.
   * @param pat Pattern-match name.
   *
   * @return Path object for file if precisely one matches, otherwise null.
   */

  fun getSingleMatchingFileFromFolder (folderName: String, pat: Regex): Path?
  {
    val files = getMatchingFilesFromFolder(folderName, pat)
    return if (1 == files.size) files[0] else null
  }


  /******************************************************************************/
  /**
   * Iterates over all or selected files in a folder, calling some supplied
   * function.
   *
   * @param folderPath Folder containing files of interest.
   * @param theFilePattern Pattern-match to select files, or null for all files.
   * @param theRegexPattern If true, filePattern is taken to be a regex; otherwise a command-line style pattern.
   * @param processor Processor to be called on each file.  If null, the files are not processed, and the method simply returns the file list.
   * @param sorter If non-null, something which determines the order in which the files should be processed.  Otherwise, they are processed in alphabetical order.
   * @return File-list or null.
   */

  fun iterateOverFilesInFolder (folderPath: String,
                                theFilePattern: Regex?,
                                processor: ((filePath: String) -> Unit)?,
                                sorter: Comparator<String>?): List<String>
  {
    /**************************************************************************/
    var filePattern = theFilePattern ?: ".*\\..*".toRegex()



    /**************************************************************************/
    val filePaths = getMatchingFilesFromFolder(folderPath, filePattern!!)
    var filePathsAsString = filePaths.map { it.toString() }
    if (null != sorter) filePathsAsString = filePathsAsString.sortedWith(sorter)
    if (null != processor) filePathsAsString.forEach { processor(it) }
    return filePathsAsString
  }


  /******************************************************************************/
  /**
   * Reads a text file and returns a list made up of the individual lines.
   *
   * @param inputFilePath Path from which lines are to be read.
   * @return List of lines.
   */

  fun readLines (inputFilePath: String): List<String>
  {
    try
    {
      return File(inputFilePath).readLines(Charsets.UTF_8)
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }
  }


  /******************************************************************************/
  /**
   * Renames a file from fromName to toName.
   *
   * @param toName New name for file.
   * @param fromName Old name for file.
   */

  fun renameFile (toName: String, fromName: String)
  {
    val renameFrom = File(fromName)
    val renameTo = File(toName)
    renameFrom.renameTo(renameTo)
  }


  /******************************************************************************/
  /**
   * Takes a file name and replaces the extension.
   *
   * @param filePath Input file name.
   * @param newExtension New extension
   * @return File path with revised extension.
   */

  fun replaceFileExtension (filePath: String, newExtension: String): String
  {
    return FilenameUtils.getFullPath(filePath) + FilenameUtils.getBaseName(filePath) + FilenameUtils.EXTENSION_SEPARATOR_STR + newExtension
  }


  /******************************************************************************/
  /**
   * Given a file name, returns a corresponding equivalent file name, comprising
   * the same path, but with the filename portion preceded by $_.
   *
   * @param filePath Input file.
   * @return Path for temporary file.
   */

  fun tempFileName (filePath: String): String
  {
    return Paths.get(filePath).parent.toString() + "\$_" + Paths.get(filePath).fileName.toString()
  }





  /******************************************************************************/
  /******************************************************************************/
  /**                                                                          **/
  /**                                Private                                   **/
  /**                                                                          **/
  /******************************************************************************/
  /******************************************************************************/

  /****************************************************************************/
  /* Gets Path entries for all the things in a given folder whose names match a
     given pattern.  Note that matching is case-insensitive, to try to ease
     the migration between Windows and Linux / Mac. */

  fun getMatchingThingsFromFolder (folderName: String, pat: Regex, thingTypes: String): List<Path>
  {
    /**************************************************************************/
    val fileNamePattern = Regex(pat.pattern, RegexOption.IGNORE_CASE) // Also had Pattern.UNICODE_CASE in Java, but there appears to be no Kotlin equivalent.)



    /**************************************************************************/
    val res: MutableList<Path> = ArrayList()
    val wantDirectory = ("D" in thingTypes)
    val wantFile = ("F" in thingTypes)



    /**************************************************************************/
    try
    {
      val stream = Files.newDirectoryStream(Paths.get(folderName))
      for (entry in stream)
      {
        val s = entry.fileName.toString()
        if (fileNamePattern.matches(s))
        {
          if (wantDirectory && Files.isDirectory(entry))
            res.add(entry)
          else if (wantFile && Files.isRegularFile(entry))
            res.add(entry)
        }
      }
    }
    catch (e: Exception)
    {
      throw StepException(e)
    }



    /**************************************************************************/
    return res
  }
}