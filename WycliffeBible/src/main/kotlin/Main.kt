import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.ArrayList

/******************************************************************************/
fun main()
{
    with (InputText)
    {
      parse()
      generateOsis()
    }

//    with (ExternalInterface)
//    {
//      handleOsis2modCall()
//      createModuleZip()
//    }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object InputText
{
  /****************************************************************************/
  fun generateOsis ()
  {
    /*************************************************************************/
    val out = File(FileLocations.OsisFilePath).bufferedWriter()
    
    
    /*************************************************************************/
    val separator = "<!--====================================================-->\n"
    out.write(makeOsisHeader())



    /*************************************************************************/
    fun insertBookTitleWhereAppropriate (bookNo: Int)
    {
      val originalBookName = m_BookNames.keys.toList()[bookNo]
      if (!originalBookName.endsWith("Esdras")) return
      out.write("  <title  type='main'>Known as $originalBookName in the Wycliffe text.</title>\n")
    }



    /*************************************************************************/
    for (bookNo in m_Books.indices)
    {
      val bookName = m_BookNames[m_BookNames.keys.toList()[bookNo]]
      if ("N/A" == bookName)
        continue

      out.write("\n\n\n\n\n")
      out.write(separator); out.write(separator); out.write(separator)

      out.write("<div osisID='$bookName' type='book'>\n")

      insertBookTitleWhereAppropriate(bookNo)

      for (chapter in m_Books[bookNo])
      {
        val chapterNo = chapter.m_ChapterNo
        if ("1" != chapterNo)
        {
          out.write("\n\n")
          out.write("  $separator")
        }

        out.write("  <chapter osisID='$bookName.$chapterNo'>\n")

        val prefix = "$bookName.$chapterNo."
        val verses = chapter.m_Verses.map {
          val ix = it.indexOf(" ")
          val ref = prefix + it.substring(0, ix)
          val text = processNotes(ref, it.substring(ix + 1), chapter.m_Notes)
          "    <verse osisID='$ref' sID='$ref'/>$text<verse eID='$ref'/>"
        } // verses

        out.write(verses.joinToString("\n"))
        out.write("\n")
        out.write("  </chapter>\n")
      } // for chapter

      out.write("</div>\n")
    } // for bookNo



    /*************************************************************************/
    out.write(makeOsisTrailer())
    out.close()
  }


  /****************************************************************************/
  fun parse ()
  {
    /**************************************************************************/
    val bookNamesKeys = m_BookNames.keys.toList()
    var bookNo = -1
    var bookName = ""
    var chapterNo = ""
    var chapterRecord: Chapter? = null
    var skip = false



    /**************************************************************************/
    var text = File(FileLocations.InputFilePath).bufferedReader().readLines().filter { it.isNotEmpty() }.map { it.trim() }
    val ix = text.indexOfFirst { "Chapter 1" == it }
    text = text.drop(ix)



    /**************************************************************************/
    for (line in text)
    {
      /************************************************************************/
      if (line.startsWith("Chapter"))
      {
        skip = false

        if ("Chapter 1" == line)
        {
          bookName = bookNamesKeys[++bookNo]
          chapterNo = "1"
          val chapters = mutableListOf<Chapter>()
          m_Books.add(chapters)
          chapterRecord = Chapter(chapterNo)
          chapters.add(chapterRecord)
        }
        else
        {
          chapterNo = line.split(" ")[1]
          val chapters = m_Books[bookNo]
          chapterRecord = Chapter(chapterNo)
          chapters.add(chapterRecord)
        }

        continue
      }



      /************************************************************************/
      if ("* * *" == line)
        skip = true



      /************************************************************************/
      if (skip)
        continue



      /************************************************************************/
      if (line.startsWith("↑"))
      {
        var x = line.replace("↑ [Note: ", "")
        x = x.substring(0, x.length - 1)
        chapterRecord!!.m_Notes.add(x)
        continue
      }

      chapterRecord!!.m_Verses.add(line)
    }
  }


  /****************************************************************************/
  private fun makeOsisHeader (): String
  {
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) // Get the current date

    return """
      <?xml version="1.0" encoding="UTF-8"?>
      <!-- OSIS created by the STEPBible project www.stepbible.org $date. -->
      <osis xmlns:osis="http://www.bibletechnologies.net/2003/OSIS/namespace"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.bibletechnologies.net/2003/OSIS/namespace http://www.bibletechnologies.net/osisCore.2.1.1.xsd">
        <osisText canonical="false" osisIDWork="Wyc" osisRefWork="Bible" xml:lang="en">
          <header>
            <work osisWork="enmWyc">
              <title>Wyclif Bible</title>
              <type type="OSIS">Bible</type>
              <identifier type="OSIS">Commentary.en.TH.StudyNotes.2024</identifier>
              <rights type="x-openAccess">Public domain</rights>
              <refSystem>Bible</refSystem>
            </work>
          </header>""".trimIndent()
  }


  /****************************************************************************/
  private fun makeOsisTrailer (): String
  {
    return """
      </osisText>
    </osis>""".trimIndent()
  }


  /****************************************************************************/
  /* Interpolates notes. */

  private fun processNotes (ref: String, text: String, notes: List<String>): String
  {
    return text.replace(Regex("\\[(\\d+)]")) { matchResult ->
      val ix = matchResult.groupValues[1].toInt() - 1 // Convert [1], [2], etc. to 0-based index
      if (ix !in notes.indices)
        throw ArrayIndexOutOfBoundsException("$text: Missing note $ix.")
      "<note n='▼' osisID='$ref!f_${1 + ix}' osisRef='$ref' type='explanation'>${notes[ix].replace("&", "&amp;")}</note>"
    }
  }


  /****************************************************************************/
  private data class Chapter (val m_ChapterNo: String)
  {
    val m_Verses: MutableList<String> = mutableListOf()
    val m_Notes: MutableList<String> = mutableListOf()
  }

  private val m_Books: MutableList<MutableList<Chapter>> = mutableListOf()

  /****************************************************************************/
  /* Maps book names in the Wycliffe text to OSIS abbreviations.  I am assuming
     that the books in the text proper appear in the order listed below. */

  private val m_BookNames = mapOf(
    "Genesis" to "Gen",
    "Exodus" to "Exod",
    "Leviticus" to "Lev",
    "Numbers" to "Num",
    "Deuteronomy" to "Deut",
    "Joshua" to "Josh",
    "Judges" to "Judg",
    "Ruth" to "Ruth",
    "1 Kings" to "1Sam",
    "2 Kings" to "2Sam",
    "3 Kings" to "1Kgs",
    "4 Kings" to "2Kgs",
    "1 Paralipomenon" to "1Chr",
    "2 Paralipomenon" to "2Chr",
    "1 Esdras" to "Ezra",
    "2 Esdras" to "Neh",
    "3 Esdras" to "1Esd",
    "Tobit" to "Tob",
    "Judith" to "Jdt",
    "Esther" to "Esth",
    "Job" to "Job",
    "Psalms" to "Ps",
    "Proverbs" to "Prov",
    "Ecclesiastes" to "Eccl",
    "Songes of Songes" to "Song",
    "Wisdom" to "Wis",
    "Syrach" to "Sir",
    "Isaiah" to "Isa",
    "Jeremiah" to "Jer",
    "Lamentations" to "Lam",
    "Preier of Jeremye" to "EpJer",
    "Baruk" to "Bar",
    "Ezechiel" to "Ezek",
    "Daniel" to "Dan",
    "Osee" to "Hos",
    "Joel" to "Joel",
    "Amos" to "Amos",
    "Abdias" to "Obad",
    "Jonas" to "Jonah",
    "Mychee" to "Mic",
    "Naum" to "Nah",
    "Abacuk" to "Hab",
    "Sofonye" to "Zeph",
    "Aggey" to "Hag",
    "Sacarie" to "Zech",
    "Malachie" to "Mal",
    "1 Machabeis" to "1Macc",
    "2 Machabeis" to "2Macc",
    "Matheu" to "Matt",
    "Mark" to "Mark",
    "Luke" to "Luke",
    "John" to "John",
    "Dedis of Apostlis" to "Acts",
    "Romaynes" to "Rom",
    "1 Corinthis" to "1Cor",
    "2 Corinthis" to "2Cor",
    "Galathies" to "Gal",
    "Effesies" to "Eph",
    "Filipensis" to "Phil",
    "Colosencis" to "Col",
    "1 Thessalonycensis" to "1Thess",
    "2 Thessalonycensis" to "2Thess",
    "1 Tymothe" to "1Tim",
    "2 Tymothe" to "2Tim",
    "Tite" to "Titus",
    "Filemon" to "Phlm",
    "Ebrews" to "Heb",
    "James" to "Jas",
    "1 Petre" to "1Pet",
    "2 Petre" to "2Pet",
    "1 Joon" to "1John",
    "2 Joon" to "2John",
    "3 Joon" to "3John",
    "Judas" to "Jude",
    "Apocalips" to "Rev",
    "Laodicensis" to "EpLao"
  )
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object ExternalInterface
{
  fun createModuleZip()
  {
    val zipPath: String = FileLocations.SwordZipFilePath
    val inputs = mutableListOf(FileLocations.SwordConfigFolderPath, FileLocations.SwordTextFolderPath)
    Zip.createZipFile(zipPath, 9, FileLocations.SwordRootFolderPath, inputs)
  }


  /****************************************************************************/
  fun handleOsis2modCall ()
  {
    val programPath = File(FileLocations.Osis2ModFilePath).toString()
    val swordExternalConversionCommand: MutableList<String> = ArrayList()
    swordExternalConversionCommand.add(programPath) // Don't enclose the path in quotes -- see note above.
    swordExternalConversionCommand.add(FileLocations.SwordTextFolderPath)
    swordExternalConversionCommand.add(FileLocations.OsisFilePath)
    swordExternalConversionCommand.add("-v")
    swordExternalConversionCommand.add("KJVA")
    swordExternalConversionCommand.add("-z")
    runCommand("Running external command to generate Sword data: ", swordExternalConversionCommand, FileLocations.ErrorFilePath)
  }


  /****************************************************************************/
  private fun quotify (s: String, quote: String = "\"") = quote + s + quote
  private fun quotifyIfContainsSpaces (s: String, quote: String = "\"") = if (" " in s) quotify(s, quote) else s


  /****************************************************************************/
  private fun runCommand (prompt: String?, command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null): Int
  {
    println(prompt + command.joinToString(" "){ quotifyIfContainsSpaces(it) })
    val pb = ProcessBuilder(command)

    if (null != errorFilePath)
    {
      pb.redirectOutput(File(errorFilePath))
      pb.redirectError(File(errorFilePath))
    }

    if (null != workingDirectory) pb.directory(File(workingDirectory))
    val res = pb.start().waitFor()
    return res
  }
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
object FileLocations
{
  const val ModuleName = "enmWyc"
  const val RootFolderPath = "C:/Users/Jamie/RemotelyBackedUp/Git/StepTextConversion/Texts/Miscellaneous/Text_enm_Wyc_publicStep"
  val OutputFolderPath = Paths.get(RootFolderPath, "_OutputPublic").toString()
  val ErrorFilePath = Paths.get(OutputFolderPath, "converterLog.txt").toString()
  val InputFilePath = Paths.get(RootFolderPath, "InputText", "wycliffe.txt").toString()
  const val Osis2ModFilePath = "C:/Program Files/Jamie/STEP/SamiOsis2mod/samisOsis2Mod.exe"
  val OsisFilePath = Paths.get(RootFolderPath, "InputOsis", "wycliffe.xml").toString()
  val SwordRootFolderPath = Paths.get(OutputFolderPath, "Sword").toString()
  val SwordConfigFolderPath = Paths.get(SwordRootFolderPath, "mods.d").toString()
  val SwordTextFolderPath = Paths.get(OutputFolderPath, "Sword", "modules", "texts", "ztext", ModuleName).toString()
  val SwordZipFilePath = Paths.get(OutputFolderPath, "$ModuleName.zip").toString()
}