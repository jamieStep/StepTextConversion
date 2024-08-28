package org.stepbible.textconverter.builders

import org.stepbible.textconverter.osisinputonly.Osis_CanonicalHeadingsHandler
import org.stepbible.textconverter.osisinputonly.Osis_DetermineReversificationTypeEtc
import org.stepbible.textconverter.osisinputonly.Osis_ElementArchiver
import org.stepbible.textconverter.osisinputonly.Osis_Preprocessor
import org.stepbible.textconverter.subelements.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.processRegexes
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.findNodesByName
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.ref.RefBase
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Document
import java.nio.file.Paths

/******************************************************************************/
/**
 * Builds a repository package.
 *
 * @author ARA "Jamie" Jamieson
 */

object Builder_InternalOsis: Builder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  override fun banner () = "Converting Internal OSIS to version required for Sword module"
  override fun commandLineOptions () = null





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Be very careful if you intend to change the order of things below (although
     hopefully you can't go too far wrong, because the various SE processors
     all check that the relevant things have run before they do).

     As regards the input ...

     On entry, Phase1TextOutput contains OSIS.  It will be valid in the sense
     that it contains only official OSIS tags and attributes (or OSIS
     extensions where those are permitted).  But it may lack verse eids, or
     if it has them, they may not be optimally positioned.

     (It may also fail to conform to the OSIS XSD in other respects too,
     because -- as explained elsewhere -- there are certain aspects of that
     which we have ended up concluding are just too difficult to live with.)

     As regards the issues with verse ends, we have two different situations
     to consider:

     - This run may have started from VL or USX, in which case I won't yet
       have generated any verse eids.

     - Or the run may have started from OSIS.  This may be third party OSIS,
       or it may be our own OSIS from a previous run.  Either way, I have had
       no control over it, and although it will probably contain eids (or else
       it would be totally invalid), there is no guarantee they are placed in
       such a way to minimise cross-boundary-markup, which is what I want.


     Regardless of the source, therefore, I remove any existing verse eids,
     and then insert new ones optimally positioned, to the extent that I can
     achieve it.  (But before I can do that, I also need to sort out tables,
     because these will normally fall foul of cross-boundary markup, and
     will complicate verse-end placement.)

     If the run started from VL or USX, I will in due course save this
     improved OSIS to InputOsis to act as a possible input for future runs
     (for example where someone finds it convenient to tweak OSIS in order to
     add tagging or whatever).  I _don't_ save it if the starting point was
     itself OSIS, because I assume we want to retain that version.

     Further processing has to be applied to the OSIS before we are in a
     position to create a module, but I do that to a separate throw-away copy
     which is held in OsisTempDataCollection. */

  override fun doIt ()
  {
    /***************************************************************************/
    Builder_InitialOsisRepresentationOfInputs.process()
    Dbg.reportProgress((banner()))



    /***************************************************************************/
    /* Inserts a comment near the front of the DOM to help identify different
       versions. */

    fun addComment (doc: Document, text: String) = Dom.insertNodeBefore(doc.documentElement.firstChild, Dom.createCommentNode(doc, " $text "))



    /***************************************************************************/
    fun x () = Logger.announceAllAndTerminateImmediatelyIfErrors() // A little shorthand.



    /***************************************************************************/
    /***************************************************************************/
    /*
       Interface to phase 1 to pick things up.
    */
    /***************************************************************************/
    /***************************************************************************/

    /***************************************************************************/
    /* Create a home for what we're about to generate. */

    //Dbg.outputText(Phase1TextOutput)
    StepFileUtils.createFolderStructure(FileLocations.getInternalOsisFolderPath())



    /***************************************************************************/
    /* Read the OSIS created by the previous processing and apply
       pre-processing. */

    //Dbg.outputText(Phase1TextOutput)

    val regexes = ConfigData.getOsisRegexes()
    if (regexes.isNotEmpty()) Dbg.reportProgress("Applying regex preprocessing.")
    var preprocessedOsisDoc = Dom.getDocumentFromText(processRegexes(ConfigData.getOsisRegexes(), Phase1TextOutput), retainComments = true); x()
    Phase1TextOutput = "" // Free up space -- we don't need the OSIS text any more.
    preprocessedOsisDoc = Osis_Preprocessor.processXslt(preprocessedOsisDoc); x()
    Osis_Preprocessor.tidyChapters(preprocessedOsisDoc); x()
    Osis_Preprocessor.insertMissingChapters(preprocessedOsisDoc); x()
    //Osis_Preprocessor.deleteXmlns(preprocessedOsisDoc)
    Dbg.reportProgress("Loading and evaluating data.")
    InternalOsisDataCollection.loadFromDocs(listOf(preprocessedOsisDoc)); x()
    RefBase.setBibleStructure(InternalOsisDataCollection.getBibleStructure()); x()  // Needed to cater for the possible requirement to expand ranges.



    /***************************************************************************/
    /* If starting from OSIS, then for validation purposes we need a second copy
     of the original text. */

    if ("osis" == ConfigData["stepOriginData"]!!)
    {
      Dbg.reportProgress("The following is not an error -- when using OSIS input, I need two copies of the OSIS: one to work on, and one against which to validate.")
      ExternalOsisDataCollection.loadFromDocs(listOf(preprocessedOsisDoc)); x()
      RefBase.setBibleStructure(ExternalOsisDataCollection.getBibleStructure()); x()
    }


    /***************************************************************************/
     val doc = InternalOsisDataCollection.getDocument()
    //Dbg.d(doc)



    /***************************************************************************/
    /***************************************************************************/
    /*
       We may or may not have verse ends in the data.  We definitely need them
       though.  The following deals with that ...
    */
    /***************************************************************************/
    /***************************************************************************/

    /***************************************************************************/
    /* Not all documents have verse-ends.  We start by deleting any that _are_
       present.  This reduces everything to the same state, and also frees
       things up so that we can decide for ourselves where the verse-ends should
       go. */

    Dbg.reportProgress("Preparing verse tags.")
    SE_VerseEndRemover(InternalOsisDataCollection).processAllRootNodes(); x()
    modifyGlossaryEntries(doc)



    /***************************************************************************/
    /* If this run started from OSIS, then we don't actually need to worry about
       the external OSIS, because that OSIS _is_ the external OSIS.  Otherwise,
       we need to create the external OSIS.  In an earlier incarnation, I was
       then keeping it in memory until I was reasonably sure the run had worked.
       However, that uses up a lot of memory to no very good purpose, so instead
       I save it here under a discouraging name, and then rename it to its
       proper name later once I know things have worked.  This means I can
       then drop it at the earliest opportunity.

       For the OSIS to be valid, we also need verse-ends.  These I insert
       in a rudimentary manner (so as to minimise the amount of processing
       which has been applied).  With the _internal_ OSIS I do rather better,
       but that comes later. */

    if ("osis" != ConfigData["stepOriginData"]!!)
    {
      ExternalOsisDataCollection.loadFromDocs(listOf(InternalOsisDataCollection.getDocument())); x()  // Copy all DOM information to external OSIS DOM ...
      ExternalOsisDataCollection.copyProcessRegistryFrom(InternalOsisDataCollection); x()             // ... along with details of what work has been applied to the text.  (I use this for defensive programming purposes -- much
      StepFileUtils.clearFolder(FileLocations.getInputOsisFolderPath())                               // Clear the folder which will be used to store the external OSIS.
      SE_BasicVerseEndInserter(ExternalOsisDataCollection).processAllRootNodes(); x()                 // Add verse-ends in a rudimentary manner.
      addComment(ExternalOsisDataCollection.getDocument(), "Externally-facing OSIS")             // Mark the DOM so we know what this is.
      SE_StrongsHandler(ExternalOsisDataCollection).processAllRootNodes(); x()                        // Canonicalise Strong's references.  (Actually, I think this should be done later, but we need it so that Patrick can further mark up NETfull2 with morphology.)
//      Dbg.d(ExternalOsisDataCollection.getDocument())
      NodeMarker.deleteAllMarkers(ExternalOsisDataCollection)
      val filePath = Paths.get(FileLocations.getInputOsisFolderPath(), "DONT_USE_ME.xml").toString()
      StepFileUtils.createFolderStructure(Paths.get(filePath).parent.toString())
      Dom.outputDomAsXml(ExternalOsisDataCollection.getDocument(), filePath, null)
    }

    //Dbg.d(InternalOsisDataCollection.getDocument())




    /***************************************************************************/
    /***************************************************************************/
    /*
       And all of the main processing.
    */
    /***************************************************************************/
    /***************************************************************************/

    /**************************************************************************/
    /* I start here with changes that everything else relies on, and then
       continue with any other changes which are easy because they don't rely
       on anything much and / or don't markedly affect other processing. */

    Osis_ElementArchiver.archiveElements(doc)                                                   // Hive off footnotes to separate document to speed up main processing.
    addComment(doc, "Internally-facing OSIS")                                              // Add a note to say what this OSIS is.
    BasicOsisTweaker.process(InternalOsisDataCollection); x()                                   // Minor changes to make processing easier (some of which may have to be undone later).
    SE_StrongsHandler(InternalOsisDataCollection).processAllRootNodes(); x()                    // Canonicalise Strong's references.



    /**************************************************************************/
    /* Things concerned with possibly significant restructuring of the text. */

    SE_TableHandler(InternalOsisDataCollection).processAllRootNodes(); x()                      // Collapses tables which span verses into a single elided verse.
    SE_ElisionHandler(InternalOsisDataCollection).processAllRootNodes(); x()                    // Expands elisions out into individual verses.
    SE_EnhancedVerseEndInsertionPreparer(InternalOsisDataCollection).processAllRootNodes(); x() // Relatively simple and hopefully uncontentious tweaks to make it easier to insert verse ends.
    SE_EnhancedVerseEndInserter(InternalOsisDataCollection).processAllRootNodes(); x()          // Positions verse ends so as to reduce the chances of cross-boundary markup.
    Osis_CanonicalHeadingsHandler(InternalOsisDataCollection).process(); x()                    // Not sure about this step at present.
    InternalOsisDataCollection.reloadBibleStructureFromRootNodes(wantCanonicalTextSize = false); x()
                                                                                                // Probably a good idea to build / rebuild the structure here.



    /**************************************************************************/
//    Dbg.d(InternalOsisDataCollection.getDocument())
    SE_BasicValidator(InternalOsisDataCollection).structuralValidation(); x()                   // Checks for basic things like all verses being under chapters.
    SE_ListEncapsulator(InternalOsisDataCollection).processAllRootNodes(); x()                  // Might encapsulate lists (but in fact does not do so currently).
//    Osis_CrossReferenceChecker.process(InternalOsisDataCollection); x()                       // Checks for invalid cross-references, or cross-references which point to non-existent places.
    handleReversification(); x()                                                                // Does what it says on the tin.
    SE_BasicValidator(InternalOsisDataCollection).finalValidationAndCorrection(); x()           // Final checks.  May create empty verses if necessary.
//    SE_SubverseCollapser(InternalOsisDataCollection).processAllRootNodes(); x()          // Collapses subverses into the owning verses, if that's what we're doing (mainly or exclusively on public modules).
    SE_CalloutStandardiser(InternalOsisDataCollection).processAllRootNodes(); x()               // Force callouts to be in house style, assuming that's what we want.
    removeDummyVerses(InternalOsisDataCollection); x()                                          // Does what it says on the tin.
    ContentValidator.process(InternalOsisDataCollection, Osis_FileProtocol,                     // Checks current canonical content against original.
                             ExternalOsisDataCollection, Osis_FileProtocol); x()
    ExternalOsisDataCollection.clearAll()                                                       // Free up memory.
    EmptyVerseHandler.preventSuppressionOfEmptyVerses(InternalOsisDataCollection); x()          // Unless we take steps, empty verses tend to be suppressed when rendered.
    EmptyVerseHandler(InternalOsisDataCollection).markVersesWhichWereEmptyInTheRawText(); x()   // Back to doing what it says on the tin.
    SE_TextAnalyser(InternalOsisDataCollection).processAllRootNodes(); x()                      // Gather up information which might be useful to someone administering texts.
    SE_FinalInternalOsisTidier(InternalOsisDataCollection).processAllRootNodes(); x()           // Ad hoc last minute tidying.
    BasicOsisTweaker.unprocess(InternalOsisDataCollection); x()                                 // Undoes any temporary tweaks which were applied to make the overall processing easier.
    SE_FinalValidator(InternalOsisDataCollection).processAllRootNodes(); x()                     // Final health checks.



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
    Dbg.reportProgress("Writing version of OSIS needed for use with osis2mod.")

    if (ConfigData.getAsBoolean("stepStartProcessFromOsis", "no") && ConfigData["stepOriginData"] == "osis")
      StepFileUtils.copyFile(FileLocations.getInternalOsisFilePath(), FileLocations.getInputOsisFilePath()!!)
    else
    {
      Osis_ElementArchiver.processElements()
      Osis_ElementArchiver.restoreElements(InternalOsisDataCollection.getDocument())
      Dom.outputDomAsXml(InternalOsisDataCollection.getDocument(),
                         FileLocations.getInternalOsisFilePath(),
              null)
                         { x -> x.replace("^lt;", "&lt;")
                                 .replace("^gt;", "&gt;")
                                 .replace("xmlns=\"\" ", "") }
    }
  }


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
    Osis_DetermineReversificationTypeEtc.process(InternalOsisDataCollection.getBibleStructure()) // Use that to work out what we need to do.

    when (ConfigData["stepReversificationType"]!!.lowercase())
    {
      "runtime" -> SE_RuntimeReversificationHandler(InternalOsisDataCollection).processAllRootNodes()
      "conversiontime" -> Osis_SE_ConversiontimeReversification(InternalOsisDataCollection).processDataCollection()
    }
  }


  /****************************************************************************/
  /* USX "w" is converted to OSIS "w", and is normally used to hold Strongs
     or morphology information.  However, in USX (and also in OSIS???) it
     can be used simply to flag a glossary entry.  We don't support
     glossaries, and retaining it confuses later processing, so where it is
     being used to mark a glossary entry, we promote its children and delete
     it. */

  private fun modifyGlossaryEntries (doc: Document)
  {
    doc.findNodesByName("w").forEach {
      if ((it["gloss"] ?: "").isEmpty() &&
          (it["lemma"] ?: "").isEmpty() &&
          (it["morph"] ?: "").isEmpty())
      {
        Dom.promoteChildren(it)
        Dom.deleteNode(it)
      }
    }
  }


  /****************************************************************************/
  /* Earlier processing -- here or in things it relies upon -- may have
     introduced dummy verses.  We now remove them. */

  private fun removeDummyVerses (dataCollection: X_DataCollection)
  {
    Dbg.reportProgress("Removing any dummy verses.")
    dataCollection.getRootNodes().forEach { rootNode ->
      Dom.findNodesByName(rootNode, dataCollection.getFileProtocol().tagName_verse(), false).filter { NodeMarker.hasDummy(it) }.forEach { Dom.deleteNode(it) }
    }
  }
}