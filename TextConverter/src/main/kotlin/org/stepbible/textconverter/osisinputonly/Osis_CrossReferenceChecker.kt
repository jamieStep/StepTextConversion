package org.stepbible.textconverter.osisinputonly

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.set
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefCollection
import org.stepbible.textconverter.support.ref.RefFormatHandlerReaderVernacular
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Checks cross-references -- eg do targets exist, are they corrrectly
 * formatted, etc.
 *
 * @author ARA "Jamie" Jamieson
 */

/******************************************************************************/
object Osis_CrossReferenceChecker: CrossReferenceChecker
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Public                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun process (dataCollection: X_DataCollection, xrefNodes: List<Node>)
  {
    Dbg.reportProgress("Checking for dangling cross-references etc.")
    m_BibleStructure = dataCollection.getBibleStructure()
    process(xrefNodes)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Private                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks ref targets exist.  The return value is a list of refs which are
     ok.  Any which are not ok are converted to plain text. */

  private fun checkTargetsExist (refs: List<Node>): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    fun processRef (node: Node)
    {
      val refKeys = RefCollection.rdOsis(node["osisRef"]!!).getAllAsRefKeys()
      val problems = refKeys
        .map { Ref.clearS(it) } // If we have cross-references which point to subverses, I'm happy to take them as being just the owning verse.
        .filter { !m_BibleStructure.thingExists(it) }
        .map { Ref.getB(it) }

      if (problems.isEmpty())
        res.add(node)
      else
      {
        IssueAndInformationRecorder.crossReferenceNonExistentTarget(node["osisRef"]!!, getOsisIdAsRefKey(node), forceError = true)
        node["type"] = "explanation" // Convert to plain footnote.
      }
    }

    refs.forEach { processRef(it) }
    return res
  }


  /****************************************************************************/
  /* All ref tags should have loc attributes and content.  STEP uses the latter
     to provide the clickable text used to access cross-references.  I kind of
     _assume_, therefore, that the content and loc should both refer to the
     same reference, with the loc being the USX version and the content being
     the vernacular version.  There is actually -- so far as I know -- no
     _requirement_ for this to be the case, so I merely issue a warning here
     if the two do not tie up. */

  private fun compareLocAndContent (refs: List<Node>)
  {
    /**************************************************************************/
    if (!m_CanReadAndWriteVernacular) return



    /**************************************************************************/
    fun validate (node: Node)
    {
      /************************************************************************/
      val usxCollection = RefCollection.rdOsis(node["loc"]!!).getAllAsRefKeys()
      val vernacularCollection: List<RefKey>?



      /************************************************************************/
      try
      {
        /**********************************************************************/
        val x = RefFormatHandlerReaderVernacular.readEmbedded(node.textContent.trim(), context=Ref.rdOsis(node["_X_belongsTo"]!!))
        val embeddedReferences = x.filterIsInstance<RefFormatHandlerReaderVernacular.EmbeddedReferenceElementRefCollection>()
        if (1 != embeddedReferences.count())
        {
          IssueAndInformationRecorder.crossReferenceInvalidVernacularText(node.textContent.trim(), getOsisIdAsRefKey(node), forceError = false, reassurance = "Cross-reference has been retained regardless.")
          return
        }



        /**********************************************************************/
        vernacularCollection = embeddedReferences[0].rc.getAllAsRefKeys()
      }
      catch (_: Exception)
      {
        IssueAndInformationRecorder.crossReferenceInvalidVernacularText(node.textContent.trim(), getOsisIdAsRefKey(node), forceError = false, reassurance = "Cross-reference has been retained regardless.")
        return
      }



      /************************************************************************/
      if (usxCollection != vernacularCollection)
        IssueAndInformationRecorder.crossReferenceInternalAndVernacularTargetDoNotMatch(node.textContent.trim(), getOsisIdAsRefKey(node), forceError = false, reassurance = "Cross-reference has been retained regardless.")
    } // fun



    /**************************************************************************/
    refs.forEach { validate(it) }
  }


  /****************************************************************************/
  private fun process (theRefs: List<Node>)
  {
    var refs = validateRefs(theRefs)
    refs = checkTargetsExist(refs)
    compareLocAndContent(refs)
  }


  /****************************************************************************/
  /* Checks the osisRef parameter is a valid reference. */

  private fun validateRefs (refs: List<Node>): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    fun processRef (node: Node)
    {
      try {
        RefCollection.rdOsis(node["osisRef"]!!)
        res.add(node)
      }
      catch (_: Exception)
      {
        IssueAndInformationRecorder.crossReferenceInvalidReference(node["osisRef"]!!, getOsisIdAsRefKey(node), forceError = true)
      }
    }

    refs.forEach { processRef(it) }
    return res
  }


  /****************************************************************************/
  private fun getOsisIdAsRefKey (node: Node): RefKey = RefCollection.rdOsis(node["osisID"]!!).getFirstAsRefKey()


  /****************************************************************************/
  private lateinit var m_BibleStructure: BibleStructure
  private var m_CanReadAndWriteVernacular =  ConfigData.getAsBoolean("stepUseVernacularFormats")
}
