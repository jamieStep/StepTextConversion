package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Node

/****************************************************************************/
/**
 * Checks cross-references.
 *
 * Cross-references normally comprise two parts -- an attribute which gives
 * the target in internal form (ie a standard USX or OSIS references), and
 * a content which typically gives the target in vernacular form.  Within
 * STEP, you click on the latter, and the internal information is used to
 * bring up the correct cross-referenced verse.
 *
 * There are a number of things which could go wrong with this, and it is the
 * purpose of the present class to report issues and where possible to
 * circumvent them:
 *
 * - The cross-reference may be perfectly valid, but may point to a target
 *    which does not exist in this text.  For example, we may have a
 *    reference to an OT verse in an NT--nly text.  These I change from
 *    being cross-references, and convert to explanatory footnotes.
 *
 * - The cross-reference may be syntactically valid but semantically invalid
 *   For example, we may have a reference to Gen 1:999.  It is not always
 *   easy to distinguish such cases from those covered in the previous
 *   bullet point, and so I treat these as discussed above.
 *
 * - The internal form of the cross-reference may be semantically invalid.
 *   These I report as errors.
 *
 * - The external form of the cross-reference may be semantically invalid
 *   or may point to a different place from the internal form.  To
 *   recognise such a situation I would need details of how to parse
 *   vernacular references, which information is seldom likely to be
 *   available (it would require manual analysis plus provision of some
 *   slightly fiddly configuration information).  Since most likely I will
 *   not be in a position to text for this, I do not cater for it here.
 *
 * - The cross-reference may contain a reference *collection* (as opposed to
 *   pointing to either a single verse, or a range).  Neither USX nor OSIS
 *   support this.  I report it as an error, and rely upon someone to tidy
 *   up the input for me.
 *
 * - The reference may point to subverses.  Here I convert the internal form
 *   to point to the owning verse, but leave the external form unchanged.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_CrossReferenceCanonicaliser: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                  Public                                **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Handles cross-reference checks etc.
  * 
  * @param dataCollection Data to be processed.
  */
  
  fun process (dataCollection: X_DataCollection)
  {
    Rpt.report(level = 1, "Checking cross-references ...")
    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable {
            Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
            Osis_CrossReferenceCheckerForBook(dataCollection).processRootNode(rootNode)
          } // asyncable
        } // forEach
      } // run
    } // report
  } // fun
}





/******************************************************************************/
private class Osis_CrossReferenceCheckerForBook (val m_DataCollection: X_DataCollection)
{
  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    val xrefNodes = rootNode.getAllNodesBelow().filter { node -> m_DataCollection.getFileProtocol().isXrefNode(node) }
    process(xrefNodes)
  }


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
        .filterNot { m_DataCollection.getBibleStructure().thingExists(it) }
        .map { Ref.getB(it) }

      if (problems.isEmpty())
        res.add(node)
      else
      {
        IssueAndInformationRecorder.crossReferenceNonExistentTarget(node["osisRef"]!!, getOsisIdAsRefKey(node), forceError = false)
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
      val formalCollection = RefCollection.rdOsis(node["loc"]!!).getAllAsRefKeys()
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
      if (formalCollection != vernacularCollection)
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
    fun removeSubverseReferences (node: Node, rc: RefCollection): Node
    {
      when (val r = rc.getElements()[0])
      {
        is Ref ->
        {
           r.clearS()
           node["osisRef"] = r.toStringOsis()
           return node
        } // Ref

        is RefRange ->
        {
          var refLow = r.getLowAsRefKey()
          var refHigh = r.getHighAsRefKey()

          if (Ref.hasS(refLow) || Ref.hasS(refHigh))
          {
            refLow = Ref.clearS(refLow)
            refHigh = Ref.clearS(refHigh)
            if (refLow == refHigh)
              node["osisRef"] = Ref.rd(refLow).toStringOsis()
            else
              node["osisRef"] = RefRange(refLow, refHigh).toStringOsis()

            return node
          }
        } // RefRange
      } // when

      throw StepExceptionWithStackTraceAbandonRun("removeSubverseReferences: Impossible case.")
    }


    /**************************************************************************/
    fun processRef (node: Node)
    {
      try {
        val rc = RefCollection.rdOsis(node["osisRef"]!!) // Simply checks we can read things.
        if (1 == rc.getElementCount())
          res.add(removeSubverseReferences(node, rc))
        else
          IssueAndInformationRecorder.crossReferenceInvalidReference(node["osisRef"]!!, getOsisIdAsRefKey(node), forceError = true)
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
  private var m_CanReadAndWriteVernacular =  ConfigData.getAsBoolean("stepUseVernacularFormats")
}
