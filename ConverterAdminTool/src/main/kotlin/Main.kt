/******************************************************************************/
/**
* Utility to extract information from Sword configuration files.
*
* I store various information about texts in Sword configuration files,
* using stylised comments.  The fact that they are comments means they can
* remain in the configuration file as supplied to osis2mod.  The fact that they
* are stylised means I can look for and parse them.
*
* This programme caters for running over files below a given root folder in order
* to carry out administrative-type functions.  At the time of writing, the only
* function available is one which looks at Sword configuration files and
* extracts the summary information from them and writes it to a tab-separated
* variable file, for which the command line is:
*
*   -rootFolder <pathToRootFolder>
*
* and the tab-separated variable file is created in this same folder.
*
* I have written this to be extensible, so if you need additional forms of
* processing, you need only create new extensions to the Processor interface,
* and change the command line handling as appropriate.
*/
/******************************************************************************/

import java.io.File
import java.nio.file.Paths
import kotlin.io.path.writeLines
import kotlin.system.exitProcess
import kotlinx.coroutines.*
import org.w3c.dom.Node



/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                 Public                                   **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
fun main (args: Array<String>)
{
  try
  {
    //val processor = ProcessorExtractSummaryInformation()
    val processor = ProcessorAdHoc()
    //val processor = ProcessorRunConverter()
    processor.process(args)
    println("Finished")
  }
  catch (e: Exception)
  {
    println(e.message)
    e.printStackTrace()
  }
}





/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                              Data classes                                **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
/* Extracts data from the stylised comment which contains the summary data in
   a Sword configuration file, and makes that data available in tab-separated
   variable form. */

private class SummaryData
{
  /****************************************************************************/
  /* Return value comprises the column headers (ie field names) tab-separated,
     and the data, tab-separated. */

  fun asString (): Pair<String, String>
  {
    val keys = m_SummaryData.keys.joinToString("\t")
    val data = m_SummaryData.values.joinToString("\t")
    return Pair(keys, data)
  }


  /****************************************************************************/
  fun extract (f: File)
  {
    f.forEachLine {
      if ("#\\s+StepAdminProtocolVersion=".toRegex().matches(it))
      {
        extract(it)
        return@forEachLine // $$$$$ Check this returns out of forEachLine and therefore returns from the function without processing any further lines.
      }
    }
  }


  /****************************************************************************/
  fun extract (s: String)
  {
    val v = s.split("|").map { it.trim() }
    v.forEach {
      val vv = it.split("=").map { it.trim() }
      m_SummaryData[vv[0]] = vv[1]
    }
  }



  /****************************************************************************/
  val m_SummaryData: MutableMap<String, String> = mutableMapOf()
}





/******************************************************************************/
/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                               Processors                                 **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
private interface Processor
{
  fun process (args: Array<String>)
}





/******************************************************************************/
private class ProcessorRunConverter: Processor
{
  /****************************************************************************/
  /* Extracts summary data from all Sword config files below the root folder
     and outputs as a tab-separated-variable file, with header, one row per
     Sword config file. */

  override fun process (args: Array<String>)
  {
    /**************************************************************************/
    CommandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    CommandLineProcessor.addCommandLineOption("args", 1, "Arguments (other than root folder) to be passed to the converter", null, null, true)
    if (!CommandLineProcessor.parse(args, "AdminDataExtractor/RunConverter"))
      return



    /**************************************************************************/
    val startOfCommandLine = "java -jar \"C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\TextConverter\\out\\artifacts\\TextConverter_jar\\TextConverter.jar\" "
    val commandLineParameters = "-rootFolder \"%\" " + CommandLineProcessor.getOptionValue("args")!!
    val folders = getFiles(CommandLineProcessor.getOptionValue("rootFolder")!!, listOf("Text_.*".toRegex())).map { it.toString() }



    /**************************************************************************/
    runBlocking {
      folders.subList(0, 1).forEach {
        launch {
          val thisCommandLine = startOfCommandLine + commandLineParameters.replace("%", it)
          runCommand(listOf(thisCommandLine))
          //println(thisCommandLine)
        }
      }
    }



    /**************************************************************************/
    //$$$ Check results here, running over all folders.  (Perhaps delete converter log before doing any of the processing here, so that absence of the log indicates something has gone badly wrong.)
  }
}


/******************************************************************************/
private class ProcessorExtractSummaryInformation: Processor
{
  /****************************************************************************/
  /* Extracts summary data from all Sword config files below the root folder
     and outputs as a tab-separated-variable file, with header, one row per
     Sword config file. */

  override fun process (args: Array<String>)
  {
    /**************************************************************************/
    CommandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, true)
    if (!CommandLineProcessor.parse(args, "AdminDataExtractor/ExtractSummary"))
      return



    /**************************************************************************/
    val data: MutableList<String> = mutableListOf()
    var doneHeader = false
    val rootFolder = CommandLineProcessor.getOptionValue("rootFolder")!!



    /**************************************************************************/
    getFiles(rootFolder, listOf("%Text_.*".toRegex(), "Sword".toRegex(), "mods\\.d".toRegex(), ".*\\.conf".toRegex())).forEach {
      val summaryData = SummaryData()
      summaryData.extract(it)
      val x = summaryData.asString()
      if (!doneHeader)
      {
        data.add(x.first)
        doneHeader = true
      }

      data.add(x.second)
    }



    /**************************************************************************/
    Paths.get(rootFolder, "allBiblesConfigSummary.tsv").writeLines(data)
  }
}


/******************************************************************************/
private class ProcessorAdHoc: Processor
{
  /****************************************************************************/
  /* Feel free to hack this as necessary.  Intended to cater for ad-hoc
     investigation of modules.  */

  override fun process (args: Array<String>)
  {
    /**************************************************************************/
    CommandLineProcessor.addCommandLineOption("rootFolder", 1, "Root folder of Bible text structure.", null, null, false)
    if (!CommandLineProcessor.parse(args, "AdminDataExtractor/AdHoc"))
      return



    /**************************************************************************/
    val rootFolder = CommandLineProcessor.getOptionValue("rootFolder") ?: "C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Dbl\\Biblica"



    /**************************************************************************/
    /* New files. */

    getFiles(rootFolder, listOf("Text_".toRegex()))
      .filterNot { "NASV" in it.name }
      .forEach {
      process(it)
    }



    /**************************************************************************/
    /* Old files. */

//   var firstTime = true
//    getFiles("C:\\Users\\Jamie\\Desktop\\Step\\BiblicaDbl\\_Modules", listOf("license\\.xml".toRegex())).forEach {
//       processLicenceDetails(it, firstTime)
//      firstTime = false
//    }
  }


  /****************************************************************************/
  private fun process (file: File)
  {
    println(file.name)

    /*
    val doc = Dom.getDocument(file.toString())

    var node = Dom.findNodeByName(doc, "DBLMetadata")!!
    val id = Dom.getAttribute(node, "id")
    val revision = Dom.getAttribute(node, "revision")

    node = Dom.findNodeByXpath(doc, "identification/name")!!
    //node = Dom.findNodeByName(node, "name", false)!!
    val name = node.textContent

    val moduleName = File(File(file.parent).parent).name.replace("%Text_", "")
    println(id + "\t" + moduleName + "\t" + revision + "\t" + name)


    val moduleName = File(File(file.parent).parent).name.replace("%Text_", "")

    var selector = ""
    var node: Node? = null

    if (null == node)
    {
      selector = "identification/abbreviationLocal"
      node = Dom.findNodeByXpath(doc, selector)
    }

    if (null == node)
    {
      selector = "publications/publication/abbreviationLocal"
      Dom.findNodeByXpath(doc, selector)
    }

    if (null == node)
    {
      selector = "identification/abbreviation"
      node = Dom.findNodeByXpath(doc, selector)
    }

    var newAbbreviation = node!!.textContent.lowercase().capitalize()
    if (!isAscii((newAbbreviation)))
      newAbbreviation = Dom.findNodeByXpath(doc, "identification/abbreviation")!!.textContent.lowercase().capitalize()

    newAbbreviation = removeNonAlphabetical(newAbbreviation)

    node = Dom.findNodeByXpath(doc, "language/iso")!!
    val newName = node.textContent + "_" + newAbbreviation

    if (moduleName != newName) println("ren %Text_$moduleName %Text_$newName & rem $selector")
    */

  }


  /****************************************************************************/
  private fun processLicenceDetails (file: File, doHeader: Boolean)
  {
    val doc = Dom.getDocument(file.toString())

    var node = Dom.findNodeByName(doc, "DBLMetadata")!!
    val licenseId = Dom.getAttribute(node, "id")

    node = Dom.findNodeByName(doc, "dateLicense")!!
    val dateLicense = node.textContent

    node = Dom.findNodeByName(doc, "dateLicenseExpiry")!!
    val dateLicenseExpiry = node.textContent

    node = Dom.findNodeByName(doc, "publicationRights")!!
    val allowIntroductions = Dom.findNodeByName(node, "allowIntroductions", false)!!.textContent
    val allowFootnotes = Dom.findNodeByName(node, "allowFootnotes", false)!!.textContent
    val allowCrossReferences = Dom.findNodeByName(node, "allowCrossReferences", false)!!.textContent
    val allowExtendedNotes = Dom.findNodeByName(node, "allowCrossReferences", false)!!.textContent

    val moduleName = File(File(file.parent).parent).name.replace("%Text_", "")

    if (doHeader)
      println("ModuleName\tLicenceId\tDateLicensed\tDateLicenceExpiry\tAllowIntroductions\tAllowFootnotes\tAllowCrossReferences\tAllowExtendedNotes")

    println("$moduleName\t$licenseId\t$dateLicense\t$dateLicenseExpiry\t$allowIntroductions\t$allowFootnotes\t$allowCrossReferences\t$allowExtendedNotes")
  }
}




/******************************************************************************/
/******************************************************************************/
/**                                                                          **/
/**                                Private                                   **/
/**                                                                          **/
/******************************************************************************/
/******************************************************************************/

/******************************************************************************/
private fun checkArgs (args: Array<String>): String
{
  if (args.isEmpty()) return Paths.get("").toAbsolutePath().toString()
  if (2 == args.size && args[0].lowercase() == "-rootfolder") return args[1]
  println("\n\nUsage:\n\n  java ... -rootFolder <pathToFolder>\n")
  exitProcess(0)
}


/******************************************************************************/
/* Looks through a directory tree rooted at rootFolder.  It starts by looking
   for files / folders which match patternMatches[0]; then under each of the
   matching entries it looks for things which match what was originally
   patternMatches[1]; etc.  The result is a list of files / folders which match
   the full collection of regexes. */

private fun getFiles (rootFolder: String, patternMatches: List<Regex>): List<File>
{
  return if (1 == patternMatches.size)
   File(rootFolder).walk()
     .filter { it.name.matches(patternMatches[0]) }
     .toList()
  else
    File(rootFolder).walk()
     .filter { it.name.matches(patternMatches[0]) }
     .flatMap { getFiles(it.toString(), patternMatches.subList(1, patternMatches.size)) }
     .toList()
}


/******************************************************************************/
fun isAscii (input: String): Boolean
{
  val asciiRegex = Regex("^\\p{ASCII}*\$")
  return input.matches(asciiRegex)
}


/******************************************************************************/
fun removeNonAlphabetical (input: String): String
{
  val regex = Regex("[^a-zA-Z]")
  return input.replace(regex, "")
}


/******************************************************************************/
fun runCommand (command: List<String>, errorFilePath: String? = null, workingDirectory: String? = null)
{
  println("Starting: " + java.lang.String.join(" ", command))
  val pb = ProcessBuilder(command)
  if (null != errorFilePath) pb.redirectError(File(errorFilePath))
  if (null != workingDirectory) pb.directory(File(workingDirectory))
  val process = pb.start()
  process.waitFor()
  println("Finished: " + java.lang.String.join(" ", command))
}





///******************************************************************************/
///******************************************************************************/
///******************************************************************************/
// I used to output to a spreadsheet.  Just in case we ever want to do this again,
// here are the details ...

///******************************************************************************/
//private fun createExcelFile (filePath: String, headers: String, rows: List<String>)
//{
//  /****************************************************************************/
//  val wkb = XSSFWorkbook()
//  val wks = wkb.createSheet()
//
//
//  /****************************************************************************/
//  val todayAsLocalDate = LocalDate.now()
//  wkb.setSheetName(0, "StepTextSummary_" + SimpleDateFormat("yy_MM_dd").format(Date()))
//
//
//
//  /****************************************************************************/
//  /* Date style. */
//
//  val  createHelper = wkb.creationHelper
//  val dateStyle =  wkb.createCellStyle()
//  dateStyle.dataFormat = createHelper.createDataFormat().getFormat("dd-mmm-yy")
//
//
//
//   /****************************************************************************/
//   /* Hyperlink style. */
//
//   val hyperlinkStyle = wkb.createCellStyle()
//   val hyperlinkFont = wkb.createFont()
//   hyperlinkFont.underline = XSSFFont.U_SINGLE
//   hyperlinkFont.color = IndexedColors.BLUE.index
//   hyperlinkStyle.setFont(hyperlinkFont)
//
//
//
//  /****************************************************************************/
//  /* Header style. */
//
//  val headerStyle = wkb.createCellStyle()
//  val font = wkb.createFont()
//  font.fontHeightInPoints = 11.toShort()
//  font.bold = true
//  headerStyle.setFont(font)
//  headerStyle.fillForegroundColor = IndexedColors.CORAL.index
//  headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
//  headerStyle.bottomBorderColor = IndexedColors.GREY_50_PERCENT.index
//  headerStyle.topBorderColor    = IndexedColors.GREY_50_PERCENT.index
//  headerStyle.leftBorderColor   = IndexedColors.GREY_50_PERCENT.index
//  headerStyle.rightBorderColor  = IndexedColors.GREY_50_PERCENT.index
//
//
//
//  /****************************************************************************/
//  /* Create the header row, turn on filtering and then autosize the columns.
//     I do this again later, but I want to make sure that the columns are at
//     least wide enough to display the headings. */
//
//  val headerCells = headers.split("\t").toMutableList()
//  headerCells.add("MonthsToExpiry"); headerCells.add("LicenceStatus")
//  val rowHeader = wks.createRow(0)
//  for (i in headerCells.indices)
//  {
//    rowHeader.createCell(i).setCellValue(headerCells[i])
//    rowHeader.getCell(i).cellStyle = headerStyle
//  }
//
//  wks.setAutoFilter(CellRangeAddress(0, 0, 0, headerCells.size - 1))
//  for (i in headerCells.indices) wks.autoSizeColumn(i)
//
//
//
//  /****************************************************************************/
//  val colExpiryDate = headerCells.indexOf("LicenceExpiryDate")
//  val colRootFolder = headerCells.indexOf("RootFolder")
//  fun addCalculatedColumns (row: MutableList<String>)
//  {
//    val expiryDate = LocalDate.parse(row[colExpiryDate])
//    val diffInMonths = Period.between(todayAsLocalDate, expiryDate).months
//    row.add(diffInMonths.toString())
//    val status = if (diffInMonths < 0) "EXPIRED" else if (diffInMonths <= 3) "Expires in next 3 months" else ""
//    row.add(status)
//  }
//
//
//
//  /****************************************************************************/
//  /* Turn the data into the equivalent of a list of rows each made up of cells.
//     Then add calculated columns, and sort according to months-to-expiry. */
//
//  var splittedData = rows.map { it.split("\t").toMutableList() }
//  splittedData.forEach { addCalculatedColumns(it) }
//  val colMonthsToExpiry = headerCells.indexOf("MonthsToExpiry")
//  splittedData = splittedData.sortedBy { it[colMonthsToExpiry].toInt() }
//
//
//
//  /****************************************************************************/
//  /* Put the data into the worksheet, and do a little reformatting etc. */
//
//  for (i in splittedData.indices)
//  {
//    val row = wks.createRow(i + 1)
//    val cells = splittedData[i]
//    for (j in cells.indices) row.createCell(j).setCellValue(cells[j])
//
//    var cell = row.getCell(colMonthsToExpiry)
//    cell.setCellValue(cell.rawValue.toDouble())
//
//    cell = row.getCell(colExpiryDate)
//    var content = splittedData[i][colExpiryDate]
//    val bits = content.split("-")
//    cell.cellStyle = dateStyle
//    cell.setCellValue(LocalDate.of(bits[0].toInt(), bits[1].toInt(), bits[2].toInt()))
//
//    cell =  row.getCell(colRootFolder)
//    content = splittedData[i][colRootFolder]
//    cell.cellFormula = "HYPERLINK(\"file:///" + content.replace("\\", "/") + "\", \"" + content + "\")"
//    cell.cellStyle = hyperlinkStyle
//  }
//
//
//
//  /****************************************************************************/
//  /* Auto size the columns again so they display their content -- except for
//     the RootFolder column, whose full content we don't need to see. */
//
//  for (i in headerCells.indices)
//  {
//    if (headerCells[i] != "RootFolder")
//      wks.autoSizeColumn(i)
//  }
//
//  wks.createFreezePane(0, 1)
//
//
//
//  /****************************************************************************/
//  val outputStream = FileOutputStream(filePath)
//  wkb.write(outputStream)
//  wkb.close()
//}
//
//
///******************************************************************************/
///* Input rows comprise fields separated by ' | '.  Split them out.  Then within
//   each field we have fieldName=value, and we want only the values.  Extract
//   these and return them as a tab-separated string.  (In fact I think they'd be
//   more useful as a list, but at one stage I was assuming I'd paste directly
//   into the spreadsheet.) */
//
//private fun getDetails (row: String): String
//{
//  val parts = row.split(" | ")
//  val headers = parts.map { it.split("=")[1].trim() }
//  return headers.joinToString("\t")
//}
//
//
///******************************************************************************/
///* Input rows comprise fields separated by ' | '.  Split them out.  Then within
//   each field we have fieldName=value, and we want only the names.  Extract
//   these and return them as a tab-separated string.  (In fact I think they'd be
//   more useful as a list, but at one stage I was assuming I'd paste directly
//   into the spreadsheet.) */
//
//private fun getHeaders (row: String): String
//{
//  val parts = row.split(" | ")
//  val headers = parts.map { it.split("=")[0].trim() }
//  return headers.joinToString("\t")
//}

  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
