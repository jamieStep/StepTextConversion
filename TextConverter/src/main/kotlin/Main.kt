/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.stepexception.StepException
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  //Dbg.setBooksToBeProcessed("Amo")
  //validateXmlFileStructure()
  //tryXslt()

  try
  {
    mainCommon(args)
    val majorWarnings = GeneralEnvironmentHandler.getMajorWarningsAsBigCharacters()
    if (majorWarnings.isNotEmpty())
    {
      print(majorWarnings)
      Logger.warning(majorWarnings)
      Logger.announceAll(false)
    }

    Dbg.endOfRun()
    println("Finished\n")
  }
  catch (e: StepException)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    if (!e.getSuppressStackTrace()) e.printStackTrace()
  }
  catch (e: Exception)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
  }
}


/******************************************************************************/
private fun mainCommon (args: Array<String>)
{
  TextConverterController().process(args)
  TestController.instance().terminate()
  Logger.summariseResults()
}


/******************************************************************************/
private fun tryXslt ()
{
  val document = Dom.getDocument("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Miscellaneous\\Text_eng_NASB2020\\RawUsx\\Psa.usx", true)
  val basicStylesheet: String = """
    <!-- Delete para:mt1 and para:mt2, because they create titles which duplicate STEP's own titles. -->
    <xsl:template match="para[matches(@style, '^mt(1|2)')]"/>
    
    
    <!-- Italicise Selah.  (I'm not sure, in fact, whether this may be a drop-off in STEP's rendering, and should therefore be fixed there. -->
    <xsl:template match="char[@style='w' and @strong='H5542']">
      <char style='it'>
        <char style='w' strong='H5542'>
          !recurse
        </char>
      </char>
    </xsl:template>
    
    
    <!-- At one time, this text erroneously used 'cd' to mark canonical psalm titles, where 'd' is required.  Not sure whether this is still an issue. -->
    <xsl:template match="element/@style[. = 'cd']">
      <xsl:attribute name="style">d</xsl:attribute>
    </xsl:template>
    
      $$$$$$$$$$$$$$  text = re.sub(r'\<para style\=\"s\"\>(PSALM \d+)\<\/para\>', r'<_X_comment _X_was="para:s">\1</_X_comment>', text) # Comment out Psalm titles on the basis that STEP does them anyway.

  """.trimIndent()

  val newDoc = Dom.applyBasicStylesheet(document, basicStylesheet)
  Dbg.outputDom(newDoc)
  exitProcess(0)
}


/******************************************************************************/
private fun validateXmlFileStructure ()
{
  val xmlFile = "c:/Users/Jamie/RemotelyBackedUp/Git/StepTextConversion/Texts/Miscellaneous/Text_eng_ESV_th/RawOSIS/ESV2016_OSIS+Strongs5.xml"
  try
  {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isIgnoringElementContentWhitespace = true
    factory.isValidating = false
    factory.isNamespaceAware = true

    val builder = factory.newDocumentBuilder()
    builder.parse(xmlFile)
  }
  catch (e: Exception)
  {
    e.printStackTrace()
  }

  exitProcess(0)
}



