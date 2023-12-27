/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepException
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File



/******************************************************************************/
/**
 * Tweaks OSIS.
 *
 * Over the years we have discovered a number of issues with the way STEP
 * renders modules.  These are effectively bugs in STEP.  However, some of them
 * can be fixed by tweaking the OSIS which feeds into the modules.  Where this
 * is feasible at reasonable cost (and where the resulting OSIS could still be
 * used by third parties), it is appreciably easier to adopt this expedient
 * rather than attempt to work out how to fix STEP: obviously fixing STEP is the
 * correct approach, but we don't really presently have the expertise to do it.
 *
 * This class takes an existing OSIS file and applies any tweaks which we have
 * identified.  (The fact that it works from an existing OSIS file means that
 * we can apply it both to USX / VL based modules and to ones where the input
 * is itself OSIS.)
 *
 * As an example of the kind of thing we are talking about here, we recently
 * discovered that with something like
 *
 *     ...>, <note ...
 *
 * the comma is sometimes dropped (but not always).  Introducing
 *
 *    <hi type='normal'/>
 *
 * before all note tags seems to fix this, and aside from the fact that it makes
 * the OSIS file larger, has no real adverse consequences either for us or, we
 * imagine, for third parties.
 *
 * @author ARA "Jamie" Jamieson
 */

class RawInputPreprocessor_osis : RawInputPreprocessor()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks the inputs, and then compares the timestamp on the input file with
     the timestamp recorded in the name of a file in the RawUsx folder. */

  override fun runMe (inputFolderPath: String): Boolean
  {
    if (!ConfigData.getAsBoolean("stepApplyStepRelatedTweaksToOsis", "Yes")) return false

    val inputFiles = StepFileUtils.getMatchingFilesFromFolder(inputFolderPath, ".*".toRegex()).map { it.toString() }
    if (inputFiles.isEmpty()) throw StepException("Raw input folder is empty.")
    if (inputFiles.size > 1) throw StepException("More than one OSIS input file")
    return !sameTimestamp(inputFiles[0], StandardFileLocations.getOsisFolderPath(), "xml")
  }


  /****************************************************************************/
  /* Note that in this method, we assume that the OSIS file has already been
     created, and we apply changes which require an understanding of the DOM
     structure.  Compare and contrast with the pre method. */

  override fun process (inputFolderPath: String)
  {
    m_Document = Dom.getDocument(StandardFileLocations.getOsisFilePath())
    var changed = false
    changed = doCommaNote() or changed
    if (changed) Dom.outputDomAsXml(m_Document, StandardFileLocations.getOsisFilePath(), null)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* We have discovered that with something like:

        ...>, <note ...

     the comma is sometimes dropped (but not always).  Introducing

        <hi type='normal'/>

     immediately before the note seems to fix this, and since it doesn't actually
     do anything, should not adversely affect third parties using the OSIS
     (although they may be a bit bemused by it).

     I attempt to be slightly more refined here, in that if it looks as though
     the text has already been tweaked in this manner, I don't do anything. */

  private fun doCommaNote (): Boolean
  {
    var changed = false

    fun insertNode (before: Node)
    {
      val newNode = Dom.createNode(m_Document, "<hi type='normal'/>")
      Dom.insertNodeBefore(before, newNode)
      changed = true
    }

    Dom.findNodesByName(m_Document, "note")
      .filter { null != it.previousSibling && "#text" != Dom.getNodeName(it.previousSibling) && "," == it.previousSibling.textContent.trim() }
      .forEach { insertNode(it) }

    return changed
  }


  /****************************************************************************/
  /* This method is called where we are taking raw OSIS as input.  It copies
     the input file to the standard OSIS folder, at the same time applying any
     changes which can be applied while treating the input as pure text (ie not
     parsing it into a DOM structure. */

  private fun doTextBasedPreparation ()
  {
    if (!StepFileUtils.fileExists(StandardFileLocations.getOsisFolderPath()))
    StepFileUtils.createFolderStructure(StandardFileLocations.getOsisFolderPath())
      StepFileUtils.getMatchingFilesFromFolder(StandardFileLocations.getOsisFolderPath(), ".*\\.xml".toRegex()).forEach { File(it.toString()).delete() }



    /************************************************************************/
    /* DIB sometimes puts \u000c into OSIS to make it more readable.
       Unfortunately, not all Kotlin XML methods accept that.  Also I have
       seen some files which contain <lg>, and aside from the fact that these
       generate a lot of vertical whitespace when rendered, osis2mod can
       hardly ever cope with them, because they end up with cross-boundary
       markup. */

    val inputFilePath = StepFileUtils.getMatchingFilesFromFolder(TextConverterProcessorRawInputManager.getRawInputFolderPath(), ".*\\.xml".toRegex())[0].toString()
    File(makeUsxBookFileName("osis", getFormattedDateStamp(inputFilePath), "xml")).bufferedWriter().use { writer ->
      File(inputFilePath).readLines().forEach {
        val s = it.replace("\u000c", "")
                  .replace("<lg>", "")
                  .replace("</lg>", "")
        writer.write(s)
        writer.newLine()
      }
    }
  }

  /****************************************************************************/
  private lateinit var m_Document: Document
}

