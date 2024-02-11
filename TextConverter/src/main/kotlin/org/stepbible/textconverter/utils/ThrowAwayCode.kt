package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

object ThrowAwayCode
{


/******************************************************************************/
fun convertLutherVLToUsx ()
{
  File("C:\\Users\\Jamie\\Desktop\\Martin_Luther_Uebersetzung_Strong_1545.txt").readLines().forEach {
  }
}


/******************************************************************************/
fun testOsis ()
{
}


/******************************************************************************/
fun makeDigest()
{
  Dbg.d(MiscellaneousUtils.getSha256("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Miscellaneous\\Text_deu_Lut1545\\InputVl\\Luther_1545+Strongs.txt"))
  exitProcess(0)
}


/******************************************************************************/
/**
* For trying out XSLT.
*/

fun tryXslt ()
{
  val document = Dom.getDocument("C:\\Users\\Jamie\\Desktop\\Test\\test.usx", true)
  val basicStylesheet: String = """
   <xsl:template match="para[@style = 'qd']">
      <xsl:copy>
            <xsl:attribute name="style">d</xsl:attribute>
    <xsl:apply-templates select="@*[not(local-name()='style')]|node()"/>
              </xsl:copy>
    </xsl:template>
   """.trimIndent()

  val newDoc = Dom.applyBasicStylesheet(document, basicStylesheet)
  Dbg.outputDom(newDoc)
  exitProcess(0)
}


/******************************************************************************/
/**
* Validates the structure of an XML file.
*/
fun validateXmlFileStructure ()
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


}