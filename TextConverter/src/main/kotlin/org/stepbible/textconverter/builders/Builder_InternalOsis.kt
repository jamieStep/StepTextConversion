package org.stepbible.textconverter.builders

import org.stepbible.textconverter.osisonly.*
import org.stepbible.textconverter.protocolagnosticutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefBase
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionBase
import org.stepbible.textconverter.applicationspecificutils.*
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Converts the initial form of OSIS into the form needed in order to generate
 * the module.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InternalOsis: Builder()
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Converting Internal OSIS to version required for Sword module."
  override fun commandLineOptions () = null


  /****************************************************************************/
  /* Be very careful if you intend to change the order of things below.

     As regards the input ...

     On entry, ExternalOsisDoc contains OSIS which I regard as being suitable
     for retention as the external-facing OSIS.

     It is guaranteed to use <chapter> rather than div:chapter, and will
     use milestone verses.  If we are starting from OSIS, those eids will
     be as placed in the original OSIS, unless the original OSIS had none,
     in which case they will be positioned immediately before the next sid.
  */
  /****************************************************************************/


  /****************************************************************************/
  override fun doIt ()
  {
    Builder_InitialOsisRepresentationOfInputs.process()
    Dbg.withReportProgressMain(banner(), ::doIt1)
  }


  /****************************************************************************/
  private fun doIt1 ()
  {
    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors() // A little shorthand.
    val archiver = PA_ElementArchiver()



    /***************************************************************************/
    /* Create a home for what we're about to generate. */

    //Dbg.outputText(Phase1TextOutput)
    StepFileUtils.deleteFolder(FileLocations.getInternalOsisFolderPath())
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())



    /***************************************************************************/
    /* There are a few things which either delete nodes from the structure
       altogether, or archive them for reinstatement later.  Both of these
       kinds of changes reduce the number of nodes to be processed, so
       probably better to make them as early as possible. */

    Dbg.reportProgress("Loading and initialising OSIS.")
    InternalOsisDataCollection.loadFromDocs(listOf(ExternalOsisDoc)); x()
    archiver.archiveElements(InternalOsisDataCollection, InternalOsisDataCollection.getFileProtocol()::isNoteNode); x()  // Hive off footnotes to separate document to speed up main processing.
    PA_StrongsHandler.process(InternalOsisDataCollection); x()                           // Canonicalise Strong's markup.
    Osis_BasicTweaker.process(InternalOsisDataCollection.getDocument()); x()             // Minor changes to make processing easier.
    Osis_ChapterAndVerseStructurePreprocessor.process(InternalOsisDataCollection); x()   // Sort out chapter structure, insert missing chapters, etc.




    /***************************************************************************/
    Dbg.reportProgress("\nLoading and evaluating data.  (I need two copies -- one to work on and one against which to validate.)")
    RefBase.setBibleStructure(InternalOsisDataCollection.getBibleStructure()); x()

    m_OriginalOsisDataCollection = Osis_DataCollection()
    m_OriginalOsisDataCollection.loadFromDocs(listOf(InternalOsisDataCollection.getDocument())); x()
    RefBase.setBibleStructure(m_OriginalOsisDataCollection.getBibleStructure()); x()



    /**************************************************************************/
    /* We can't necessarily rely upon verse eids being in the best positions.
       I begin here by deleting them, before reinstating them later.  There are
       then a few things which have to be run while the eids are absent. */

    Dbg.reportProgress("\nRemoving eids; sorting out tables and elisions.")
    val dummyVerses = insertDummyVerses(InternalOsisDataCollection); x() // Insert dummy verses at the ends of chapters so we can always insert _before_ something.
    PA_VerseEndRemover.process(InternalOsisDataCollection); x()          // Removes verse-ends.
    PA_TableHandler.process(InternalOsisDataCollection); x()             // Collapses tables which span verses into a single elided verse.
    PA_ElisionHandler.process(InternalOsisDataCollection); x()           // Expands elisions out into individual verses.
    
    
    
    /**************************************************************************/
    /* Time to put the eids back again, but hopefully in a better location. */
    
    Dbg.reportProgress("\nReinserting eids.")
    PA_EnhancedVerseEndInsertionPreparer.process(InternalOsisDataCollection); x() // Relatively simple and hopefully uncontentious tweaks to make it easier to insert verse ends.
    PA_EnhancedVerseEndInserter.process(InternalOsisDataCollection); x()          // Positions verse ends so as to reduce the chances of cross-boundary markup.
    
    
    
    /**************************************************************************/
    /* Standardise canonical heading details. */

    PA_CanonicalHeadingsHandler.process(InternalOsisDataCollection); x()  // Canonicalises canonical headings, as it were.
    PA_ListEncapsulator.process(InternalOsisDataCollection); x()          // Might encapsulate lists (but in fact does not do so currently).


    
    /**************************************************************************/
    /* It's probably a good idea to build / rebuild the structure details. */
    
    InternalOsisDataCollection.reloadBibleStructureFromRootNodes(wantCanonicalTextSize = true); x()
                                                             


    /**************************************************************************/
    Dbg.reportProgress("\nPerforming initial validation.")
    Osis_BasicValidator.structuralValidation(InternalOsisDataCollection); x()            // Checks for basic things like all verses being under chapters.



    /**************************************************************************/
    Dbg.reportProgress("\nPerforming reversification if necessary.")
    handleReversification(); x()   // Does what it says on the tin.



    /**************************************************************************/
    Dbg.reportProgress("\nPerforming final structural validation.")
    Osis_BasicValidator.finalValidationAndCorrection(InternalOsisDataCollection); x()    // Final checks.  May create empty verses if necessary.



    /**************************************************************************/
    /* We have to collapse subverses into the owning verse, because osis2mod /
       JSword can't cope with subverses.  However, it has to come _after_
       reversification, because that may, itself, do things with subverses, and
       we don't want to tread upon its toes.  And it also has to come after
       final validation in case the change in structure confuses things. */

    PA_SubverseCollapser.process(InternalOsisDataCollection); x()      // Collapses subverses into the owning verses.
    removeDummyVerses(dummyVerses); x()                                // Earlier processing will have introduced dummy verses at the ends of chapters.  Get rid of them.



    /**************************************************************************/
    Dbg.reportProgress("\nValidating against original text.")
    PA_ContentValidator.process(InternalOsisDataCollection, m_OriginalOsisDataCollection); x()  // Checks current canonical content against original.
    m_OriginalOsisDataCollection.clearAll()                                                     // Free up memory.



   /**************************************************************************/
   Dbg.reportProgress("\nYet more tidying.")
    PA_EmptyVerseHandler.preventSuppressionOfEmptyVerses(InternalOsisDataCollection); x() // Unless we take steps, empty verses tend to be suppressed when rendered.

    PA_EmptyVerseHandler(InternalOsisDataCollection.getFileProtocol())                     // What it says on the tin.
      .markVersesWhichWereEmptyInTheRawText(InternalOsisDataCollection); x()

    PA_TextAnalyser.process(InternalOsisDataCollection)  ; x()                             // Gather up information which might be useful to someone administering texts.
    Osis_FinalInternalOsisTidier().process(InternalOsisDataCollection); x()                // Ad hoc last minute tidying.
    Osis_BasicTweaker.unprocess(InternalOsisDataCollection); x()                           // Undoes any temporary tweaks which were applied to make the overall processing easier.
    PA_FinalValidator.process(InternalOsisDataCollection); x()                             // Final health checks.



    /**************************************************************************/
    /* Save the OSIS so it's available to osis2mod.

       We have two options here.

       The more normal one appears in the else clause, and writes the internal
       OSIS to the output file.

       In this respect, I don't really like the final code fragment (^lt etc)
       below.  It's there to undo temporary changes to text content which may
       have been applied much earlier in the processing (in this case, to replace
       things like '&lt;', because many transformations applied here start
       getting confused as to whether we have '&lt;' or '<', the latter being a
       problem in some cases).  But this architecture means that we apply these
       changes somewhere miles away in the code (in X_DataCollection), and then
       undo them again here, which isn't ideal.

       Note, incidentally, that this is different from BasicOsisTweaker.unprocess.
       That applies changes to the DOM.  Here we are applying changes to the XML
       text to which the DOM is converted.  Hence, unfortunately, it's not
       possible to combine the two steps into one.

       I said there were two options.  The other ignores all the work done thus
       far in creating an internal DOM representation, and simply assumes (where
       starting from OSIS) that the original OSIS was ok).  I have added this
       option because with ESV we do start from OSIS, and there was some concern
       that we had a version of ESV which was apparently working, and didn't
       want to risk introducing errors into it.

       If used commonly, this is a somewhat risky undertaking, because I don't
       do things to the internal OSIS for the sake of it: for instance, I make
       sure there are no holes in the versification scheme, and I also apply
       tweaks which are needed to ensure the rendering looks ok.  If we don't
       use the internal OSIS, any such tweaks are lost.

       Note that I still need to generate the internal OSIS, even if I then
       throw it away, because one of the side-effects of doing that is that I
       generate the supporting information needed for the various configuration
       files (the Sword configuration file, the samification JSON file, etc).
       This highlights another potential issue, in that this data will be based
       upon the _internal_ OSIS.  We will now be replacing that internal OSIS
       with the original OSIS, so we have to assume that the two are
       sufficiently similar that the supporting information remains relevant.
       This is definitely not a foregone conclusion. */

    ConfigData.makeBibleDescriptionAsItAppearsOnBibleList(InternalOsisDataCollection.getBookNumbers())

    if (ConfigData.getAsBoolean("stepStartProcessFromOsis", "no") && ConfigData["stepOriginData"] == "osis")
    {
      Dbg.reportProgress("\nWriting version of OSIS needed for use with osis2mod.")
      StepFileUtils.copyFile(FileLocations.getInternalOsisFilePath(), FileLocations.getInputOsisFilePath()!!)
    }
    else
    {
      // Note that PA_CalloutStandardiser and Osis_CrossReferenceChecker can't be used earlier, because we archived all of the notes nodes and removed then from the document.
      Dbg.reportProgress("\nRestoring notes nodes etc and writing version of OSIS needed for use with osis2mod.")
      archiver.restoreElements(InternalOsisDataCollection)
      PA_CalloutStandardiser.process(InternalOsisDataCollection); x() // Force callouts to be in house style, assuming that's what we want.
      Osis_CrossReferenceChecker.process(InternalOsisDataCollection)
      Dom.outputDomAsXml(InternalOsisDataCollection.getDocument(),
                         FileLocations.getInternalOsisFilePath(),
              null)
                         { x -> x.replace("^lt;", "&lt;")
                                 .replace("^gt;", "&gt;")
                                 .replace("xmlns=\"\" ", "") }
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /***************************************************************************/
  /* Time to worry about reversification.  First off, we need to read the
     data and determine how far adrift of NRSVA we are, so we can decide what
     kind of reversification (if any) might be appropriate.

     There are then three possible values for stepReversificationType:

     - runtime: This says that any restructuring will be carried out within
       STEPBible at run-time when necessary to support added value features.

     - conversiontime: This says that we will restructure the text during the
       conversion process so as to produce something which is already NRSV(A)
       compliant.  This will probably not be used much, because the sort of
       large-scale changes which it gives rise to are usually rules out by
       licence conditions.  (We may use it on public domain texts aimed mainly
       at an academic audience who can understand what's going on.)

     - none: This rules out most restructuring (but not all - see below).  It
       will be selected where we are definitely generating public domain texts
       and conversion-time restructuring has not been chosen; and where texts
       are already NRSV(A) compliant, so that no restructuring is required.

     There is one particular consideration -- missing verses.  Even where texts
     are 'complete' in the sense that they contain a full complement of books
     and chapters, it is not always the case that they contain all verses.

     Sometimes, verses may be missing at the ends of chapters.  In general, this
     is not a problem -- nothing will break as a result.  But sometimes verses
     may be missing within the body of a chapter, and this _is_ a problem.

     The reversification data foresees this issue in 'recognised' cases and
     includes data rows which address it.  But it is always possible that
     texts may lack verses nonetheless.
   */

  private fun handleReversification ()
  {
    //Dbg.d(InternalOsisDataCollection.getDocument())
    ReversificationData.process(InternalOsisDataCollection) // Read the data.
    Osis_DetermineReversificationTypeEtc.process() // Use that to work out what we need to do.

    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "runtime" -> PA_RuntimeReversificationHandler.process(InternalOsisDataCollection)
      "conversiontime" -> throw StepExceptionBase("Conversion-time reversification probably doesn't work at present.") // Osis_SE_ConversiontimeReversification(InternalOsisDataCollection).processDataCollection()
    }
  }


  /****************************************************************************/
  /* Inserts dummy verse sids at the ends of chapters so we always have
     something we can insert stuff _before_. */

  private fun insertDummyVerses (dataCollection: X_DataCollection): List<Node>
  {
    /**************************************************************************/
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    fun addDummyVerseToChapter (chapterNode: Node)
    {
      val dummySidRef = dataCollection.getFileProtocol().readRef(chapterNode[dataCollection.getFileProtocol().attrName_chapterSid()]!!)
      dummySidRef.setV(RefBase.C_BackstopVerseNumber)
      val dummySidRefAsString = dataCollection.getFileProtocol().refToString(dummySidRef.toRefKey())
      val dummySid = chapterNode.ownerDocument.createNode("<verse ${dataCollection.getFileProtocol().attrName_verseSid()}='$dummySidRefAsString'/>")
      NodeMarker.setDummy(dummySid)
      chapterNode.appendChild(dummySid)
      res.add(dummySid)
    }



    /**************************************************************************/
    val chapterNodes = dataCollection.getRootNodes().forEach { bookNode ->
      bookNode.findNodesByName(dataCollection.getFileProtocol().tagName_chapter()).forEach(::addDummyVerseToChapter)
    }



    /**************************************************************************/
    return res
  }


   /****************************************************************************/
  /* Earlier processing -- here or in things it relies upon -- may have
     introduced dummy verses.  We now remove them. */

  private fun removeDummyVerses (dummyVerses: List<Node>)
  {
    Dbg.withReportProgressSub("Removing any dummy verses.") {
      dummyVerses.forEach(Dom::deleteNode)
    }
  }


  /****************************************************************************/
  private lateinit var m_OriginalOsisDataCollection: Osis_DataCollection
}
