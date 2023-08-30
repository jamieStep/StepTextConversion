/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperRawUsx
import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.reportBookBeingProcessed
import org.stepbible.textconverter.support.miscellaneous.MiscellaneousUtils.sidify
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.support.ref.RefRange
import org.stepbible.textconverter.support.usx.Usx
import org.w3c.dom.Document
import org.w3c.dom.Node


/******************************************************************************/
/**
* Carries out any final USX validation prior to generating OSIS.
*
* This is a bit of a mixed bag.  I want to try to pick up errors without having
* to check the entire text manually.  At the same time I don't want something
* so complex that the validation itself is likely to have errors in it; and nor
* do I want to find myself merely reimplementing other parts of the system in
* order to prove that I can do the same (possibly wrong) thing twice.
*
* In fact, I rather fail on that last point, since one of the things I do is
* to check that reversified output looks the way I anticipate -- but then
* the whole of the reversification processing is based upon that same
* anticipation.  This kinda means that I'm checking my expectations against
* those same expectations.  I guess, though, that I am at least also checking
* that the processing itself has worked as I think it should.
*
* In summary, therefore, I'm presently doing (or not doing) the following:
*
* - I check the overall structure -- are all chapters in the right books, all
*   verses in the right chapters etc, verses in order and so on.
*
* - I check the canonical content of verses both where they are and where
*   they are not affected by reversification; and I also check the content
*   of canonical headers.
*
* - I *don't* check anything other than canonical content -- I don't check
*   headings, footnotes, cross-references etc.  In fact this is probably the
*   major shortcoming of what I do here, because we have very specific
*   requirements regarding annotation when reversification is applied.
*   The problem is a) that these requirements are currently not all that
*   stable; and b) that they will be very difficult to validate.
*
* @author ARA 'Jamie' Jamieson
*/

object EnhancedUsxValidator: TextConverterProcessorBase ()
{
  /****************************************************************************/
  override fun banner (): String
  {
    return "Validating"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return C_ConfigurationFlag_DoUsxFinalValidation
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    if (runMe()) doIt()
    return true
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Control                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Runs over all files in the EnhancedUsx folder, checking each one. */

  private fun doIt ()
  {
    m_ImplicitReversificationRenumbers = ReversificationData.getImplicitRenumbers()
    m_ReversificationRowsForAllBooks = ReversificationData.getAllAcceptedRows()

    val paths = StepFileUtils.getMatchingFilesFromFolder(StandardFileLocations.getEnhancedUsxFolderPath(), StandardFileLocations.getEnhancedUsxFilePattern(), false)
    paths.forEach { checkBook(it.toString()) }
  }


  /****************************************************************************/
  private fun checkBook (filePath: String)
  {
   /**************************************************************************/
   val enhancedDocument = Dom.getDocument(filePath)
   reportBookBeingProcessed(enhancedDocument)
   val bookNode = Dom.findNodeByName(enhancedDocument, "book") ?: Dom.findNodeByName(enhancedDocument, "_X_book")!!
   m_EnhancedBookAbbreviation = Dom.getAttribute(bookNode, "code")!!
   val enhancedBookNumber = BibleBookNamesUsx.abbreviatedNameToNumber(m_EnhancedBookAbbreviation)



    /**************************************************************************/
    /* Get the basic data structures with which we need to work. */

    m_EnhancedBookAnatomy = getBookAnatomy(enhancedDocument) // Maps relating sid refKeys to sid nodes, etc.
    val reversificationDetails = getReversificationDetails(enhancedBookNumber) // All of the reversification rows which target this book.
    val sourceBookNumbers = listOf(enhancedBookNumber) union reversificationDetails.m_AllRows.map { Ref.getB(it.sourceRefAsRefKey) }.toMutableSet() // This book itself (needed where we're not doing reversification), plus all the books which feed into this book under reversification.
    getAllSourceMappings(sourceBookNumbers)  // For each source book, set up anatomy details similar to those in m_EnhancedBookAnatomy.



    /**************************************************************************/
    /* The order here matters, because later processing relies upon earlier
       processing having removed 'awkward' verses from the list of those still
       to be handled. */

    if (TextConverterProcessorReversification.runMe())
    {
      checkReversifiedEverythingButCanonicalTitles(reversificationDetails)
      checkReversifiedCanonicalTitles(reversificationDetails)
    }

    checkNonReversified(enhancedBookNumber)
    checkResidual()
  }





  /****************************************************************************/
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                Verses not affected by reversification                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Checks verses carried through from the source without being affected by
     reversification.  This needs to be called at the end of processing, at
     which point the only verses remaining in the data structure will be the
     ones not taken care of by reversification. */

  private fun checkNonReversified (enhancedBookNumber: Int)
  {
   val inputAnatomy = m_RawBookAnatomies[enhancedBookNumber] ?: return

   /***************************************************************************/
    fun check (refKey: RefKey)
    {
      /************************************************************************/
      val enhancedSid = m_EnhancedBookAnatomy.m_SidToIndex[refKey]!!
      val enhancedEid = m_EnhancedBookAnatomy.m_EidToIndex[refKey]!!



      /************************************************************************/
      /* There are certain verses for which tests are inappropriate.

         - We don't want to apply tests to dummy verses.

         - It may be the case that certain verses didn't exist in the
           original -- either because they genuinely weren't there at
           all, or because they formed part of an elision in the original.
           In both cases, processing will have created empty versions of
           them, but there is nothing to check against.  This is the 'null =='
           test below.

         - In fact, we can ignore any verse marked with _X_reasonEmpty, because
           chances are that it does actually contain some content to show that
           we genuinely know that it's empty, but this content won't be the
           same as that in the original.

         - There is also one case related to tables which needs to be
           excluded.  Depending upon which form of table processing we have
           opted to apply, we may have taken a table which spans multiple
           verses and emptied out all verses but one, creating an elision
           which puts the entire table into the last verse.  This elision
           will then itself have been subject to the normal elision processing,
           such that all but this last verse will be empty.  In this case,
           the original verses will exist and have content (and hence the
           'null==' test below won't pick things up); but they will legitimately
           be empty in the enhanced text, so we need to avoid checking them.
           (The full content is picked up and handled in one of the code paras
           below, when we are processing the one verse of the elision which now
           contains the entire content.) */

      var skipTest = Dom.hasAttribute(m_EnhancedBookAnatomy.m_AllNodes[enhancedSid], "_TEMP_dummy")

      if (!skipTest) skipTest = null == inputAnatomy.m_SidToIndex[refKey]

      if (!skipTest) skipTest = Dom.hasAttribute(m_EnhancedBookAnatomy.m_AllNodes[enhancedSid], "_X_reasonEmpty")



      /************************************************************************/
      /* We always want to flag that we've checked this verse, even if, in fact,
         we're not applying any checks. */

      done(refKey)
      if (skipTest) return



      /************************************************************************/
      /* We're always interested in the content of a single enhanced verse, so
         that much is straightforward. */

      val enhancedContent = gatherContent(m_EnhancedBookAnatomy.m_AllNodes, enhancedSid, enhancedEid)



      /************************************************************************/
      /* The original text which we compare against, unfortunately, is more
         complicated, because of one special case.

         In most cases, it is indeed straightforward, because we merely take
         the raw text verse which corresponds to the one whose enhanced verse
         we have just picked up.

         The difficulty comes with the kind of table processing referred to
         above (ie the one where a table originally made up of a lot of separate
         verses has been converted to a large elision).

         The empty verses created by this have already been excluded by the
         checks above, but we're left with the one verse which now contains
         the entire table.  We can recognise this situation from the fact
         that the sid parameter of the enhanced verse gives a range (ie
         contains a dash); and in this case we want to concatenate the
         content of all of the individual input verses implied by that
         range. */

      val enhancedSidNode = m_EnhancedBookAnatomy.m_AllNodes[enhancedSid]
      val enhancedSidRefAsString = if (Dom.hasAttribute(enhancedSidNode, "_X_originalId")) Dom.getAttribute(enhancedSidNode, "_X_originalId")!! else Dom.getAttribute(enhancedSidNode, "sid")!!

      val lowRefKey: RefKey
      val highRefKey: RefKey

      if ("-" in enhancedSidRefAsString)
      {
        val rng = RefRange.rdUsx(enhancedSidRefAsString)
        lowRefKey = rng.getLowAsRefKey()
        highRefKey = rng.getHighAsRefKey()
      }
      else
      {
        lowRefKey = Ref.rdUsx(enhancedSidRefAsString).toRefKey()
        highRefKey = lowRefKey
      }

      var inputContent = ""

      for (i in lowRefKey .. highRefKey)
      {
        val rawSid = inputAnatomy.m_SidToIndex[i] ?: continue
        val rawEid = inputAnatomy.m_EidToIndex[i]!!
        inputContent += " " + gatherContent(inputAnatomy.m_AllNodes, rawSid, rawEid)
      }

      compare(enhancedContent, inputContent, refKey, refKey)
    } // fun

    m_EnhancedBookAnatomy.m_SidToIndex.keys.forEach { check(it) }
    done()
  }


  /****************************************************************************/
  /* Looks for any remaining verses we haven't dealt with. */

  private fun checkResidual ()
  {
    fun process (refKey: RefKey)
    {
      val ix = m_EnhancedBookAnatomy.m_SidToIndex[refKey]!!
      if (!Dom.hasAttribute(m_EnhancedBookAnatomy.m_AllNodes[ix], "_TEMP_dummy"))
      {
        //Dbg.d(m_EnhancedBibleAnatomy.m_AllNodes[ix].ownerDocument)
        error(refKey, "Verse not covered by validation.")
      }
    }

    m_EnhancedBookAnatomy.m_SidToIndex.keys.forEach { process(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                   Reversification and psalm titles                     **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Actions related to psalm titles are marked either as PsalmTitle or as
     RenumberTitle.

     PsalmTitle involves either leaving an existing title as-is (apart from
     annotating it), or moving it to a different chapter.

     PsalmTitle I ignore here because it always entails retaining an existing
     title.  True it may also then have a footnote added to it, but I am not
     checking here for footnotes.

     RenumberTitle is more complicated.  It may entail moving an existing title
     to a different chapter, or it may involve turning either the first verse or
     the first two verses of a chapter
   */

  private fun checkReversifiedCanonicalTitles (reversificationDetails: ReversificationDetails)
  {
    /**************************************************************************/
    /* Something marked as title in the sourceRef field always turns into
       a title in the enhanced text.  To cater for this particular form of
       manipulation, therefore, I have only to look at the sourceRef.*/

    reversificationDetails.m_CanonicalTitleRows
      .filter { ReversificationData.C_SourceIsPsalmTitle   == it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle) }
      .forEach { checkReversifiedCanonicalTitles_TitleToTitle(it)}



     /**************************************************************************/
     /* That just leaves the rows where the input is _not_ a title, but the
        target is.  I need to group these by standard book/chapter so that
        I can know whether I'm looking at one input verse or several. */

    reversificationDetails.m_CanonicalTitleRows
      .filter { ReversificationData.C_SourceIsPsalmTitle   != it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle  ) &&
                ReversificationData.C_StandardIsPsalmTitle == it.processingFlags.and(ReversificationData.C_StandardIsPsalmTitle) }
      .groupBy { it.standardRef.toRefKey_bc() }
      .forEach { checkReversifiedCanonicalTitles_VersesToTitle(it.key, it.value)}
   }


  /****************************************************************************/
  /* Handles the case where we are either leaving an existing title as-is (apart
     from annotating it), or moving it to a different chapter.

     Note that although the reversification data alludes to eg Psa.5:Title in
     these cases, the earlier reversification processing will have changed
     it to Psa.5:0. */

  private fun checkReversifiedCanonicalTitles_TitleToTitle (row: ReversificationDataRow)
  {
    val inputBibleAnatomy = m_RawBookAnatomies[Ref.getB(row.sourceRefAsRefKey)]!!
    val inputPara    = inputBibleAnatomy.m_chapterSidToPsalmTitle[row.sourceRefAsRefKey  ]!!
    val enhancedPara = m_EnhancedBookAnatomy.m_chapterSidToPsalmTitle[row.standardRefAsRefKey]!!
    val inputContent = gatherContentForCanonicalTitle(inputPara)
    val enhancedContent = gatherContentForCanonicalTitle(enhancedPara)
    compare(enhancedContent, inputContent, row.standardRefAsRefKey, row.sourceRefAsRefKey)
    // No need to call 'done' here because done is concerned only with verses, and here we are effectively dealing with a chapter.
  }


  /****************************************************************************/
  /* Handles the case where we are turning one, or perhaps two, input verses
     into a canonical title. */

  private fun checkReversifiedCanonicalTitles_VersesToTitle (owningStandardRefKey: RefKey, rows: List<ReversificationDataRow>)
  {
    // Dbg.d(owningStandardRefKey.toString())
    fun getInputRefKeyUsingSourceField   (row: ReversificationDataRow): RefKey { return row.sourceRefAsRefKey }

    val enhancedPara = m_EnhancedBookAnatomy.m_chapterSidToPsalmTitle[owningStandardRefKey]!!
    val enhancedContent = gatherContentForCanonicalTitle(enhancedPara)

    val inputBibleAnatomy = m_RawBookAnatomies[Ref.getB(rows[0].sourceRefAsRefKey)]!!
    val inputContent = gatherContentForAllConstituents(inputBibleAnatomy, ::getInputRefKeyUsingSourceField, rows)

    compare(enhancedContent, inputContent, owningStandardRefKey, rows[0].sourceRefAsRefKey)
    // No need to call 'done' here because done is concerned only with verses, and here we are effectively dealing with a chapter.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**          Reversified verses not involving canonical titles             **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* With the exception of psalm titles (dealt with elsewhere), all of the
     checks are just variations on a theme ...

     * We are always looking at an _enhanced_ verse, and (mostly) comparing it
       with _something_.

     * The _something_ we are comparing it with is always in one of the _raw_
       texts which feed into the book we are checking.

     * With anything flagged as Renumber (and this includes Moves), the
       something we are comparing it with is given by the SourceRef.

     * With anything else (Merged, Keep, Empty), the something we compare it
       with is actually the _StandardRef_ of the raw USX, because all of
       these basically simply annotate an existing verse.  (I've never
       really quite understood why these rows have a SourceRef entry at
       all, nor why sometimes the SourceRef differs from the StandardRef
       even on KeepVerse rows.)

     * Taking Merged, Keep and Empty together, on some rows we're happy to
       have had the reversification processing create an empty verse if the
       corresponding standard ref did not already exist.  These are marked
       with C_CreateIfNecessary.  (I have a feeling I'm not being as rigorous
       as perhaps I might be in checking these.)

     * Any of these may involve subverses in either the source or standard ref
       as per the reversification data.  (There may also be subverses if the
       raw text contained them and reversification has done nothing with them.)
       At the time of writing, we are not intending to retain subverses in the
       module -- we intend that subverses should be collapsed into the owning
       verse, although I am not relying upon that -- I have made it
       configurable.  In any case, to make life easier here, I _haven't_ yet
       collapsed them (I do that in the process of generating the OSIS).
       This means that subverses can be treated in exactly the same way as
       verses.
     */

  private fun checkReversifiedEverythingButCanonicalTitles (reversificationDetails: ReversificationDetails)
  {
    /**************************************************************************/
    /* Functions to obtain a refKey from a reversification row. */

    fun getInputRefKeyUsingSourceField   (row: ReversificationDataRow): RefKey { return row.sourceRefAsRefKey   }
    fun getInputRefKeyUsingStandardField (row: ReversificationDataRow): RefKey { return row.standardRefAsRefKey }



    /**************************************************************************/
    /* Controls the actual checking. */

    fun check (theRows: List<ReversificationDataRow>)
    {
      /**************************************************************************/
      //Dbg.d("==============="); Dbg.d(theRows)



      /**************************************************************************/
      /* We may do something to a verse and also apply a later KeepVerse to it.
         (The 'something' referred to here may itself be a KeepVerse, although
         it certainly doesn't have to be.)  The later KeepVerse is always there
         purely to add annotation, so if we have a KeepVerse anywhere after
         position 1, we need to drop it, or we risk creating a content string
         which duplicates the input. */

      val rows = theRows.filterIndexed { ix, row -> ix == 0 || "keep" !in row.action }



      /**************************************************************************/
      /* If reversification was permitted to create an empty verse, we look to
         see if the verse is indeed empty (I flag empty verses with
         _X_verseEmptyReason), and if it is, we can return immediately, because
         things are ok. */

      if (0 != theRows[0].processingFlags.and(ReversificationData.C_CreateIfNecessary))
      {
        val sidNode = m_EnhancedBookAnatomy.m_AllNodes[m_EnhancedBookAnatomy.m_SidToIndex[theRows[0].standardRefAsRefKey]!!]
        if (Dom.hasAttribute(sidNode, "_X_reasonEmpty"))
        {
          done(rows[0].standardRefAsRefKey)
          return
        }
      }



      /**************************************************************************/
      /* If we're renumbering (which includes RenumberInSitu and Move), we're
         going to want to take input from the verse identified by the source
         ref, otherwise from the verse identified by the standard ref. */

      val refKeySelector = if (0 == rows[0].processingFlags.and(ReversificationData.C_Renumber)) ::getInputRefKeyUsingStandardField else ::getInputRefKeyUsingSourceField
      val inputBookAnatomy = m_RawBookAnatomies[Ref.getB(refKeySelector(rows[0]))]

      val inputContent = if (rows[0].action.contains("merge")) "" else gatherContentForAllConstituents(inputBookAnatomy!!, refKeySelector, rows)
      val enhancedContent = gatherContentForAllConstituents(m_EnhancedBookAnatomy, ::getInputRefKeyUsingStandardField, listOf(rows[0]))

      if (enhancedContent.startsWith("[Missing"))
      {
        if (!rows[0].standardRef.hasS())
        {
          val message = "Missing verse: '${rows[0].standardRef}'"
          error(rows[0].standardRefAsRefKey, message)
        }
      }
      else
        compare(enhancedContent, inputContent, rows[0].standardRefAsRefKey, rows[0].sourceRefAsRefKey)

      done(rows[0].standardRefAsRefKey)
    } // fun check



    /**************************************************************************/
    /* standardRefKeysForRowsOfInterest holds the refKeys of all the standard
       references for the reversification rows affecting the book we are
       examining (except that we aren't dealing with any Psalm title rows in
       this present method).

       Each of these refKeys is associated, in m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows,
       with a list of one more reversification rows which indicate how that
       standard verse was constructed from the input.

       We take each refKey in turn, and then check it against the various inputs
       which feed into it. */

    val standardRefKeysForRowsOfInterest = reversificationDetails.m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows.keys
    standardRefKeysForRowsOfInterest.forEach { check(reversificationDetails.m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows[it]!!) }
    done()
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Miscellaneous                              **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* The &#160; below is a non-breaking space which I have to introduce in
     some cases to prevent 'empty' verses from being suppressed.  For
     comparison purposes I have to treat it as though it's a space, because
     that's what the raw text will contain.

     Similarly, &#2013 is an en-dash. */

  private fun compare (enhanced: String, input: String, enhancedRefKey: RefKey, inputRefKey: RefKey)
  {
    val contentEnhanced = enhanced.replace("\\s+|&#160;".toRegex(), " ")
                                  .replace("\\s+|&#x2013;".toRegex(), " ")
                                  .replace("&nbsp;", " ")
                                  .replace("\\s+".toRegex(), " ").trim()

    val contentInput    = input   .replace("\\s+".toRegex(), " ").trim()
    if (contentInput == contentEnhanced) return

    val message = "Verse mismatch:<nl>  Enhanced = '$contentEnhanced'<nl>  Raw      = '$contentInput'<nl>"
    error(enhancedRefKey, message)
  }


  /****************************************************************************/
  /* Removes from the mapping structures those items which we flagged previously
     for deletion. */

  private fun done ()
  {
    var keysToBeRemoved = m_EnhancedBookAnatomy.m_SidToIndex.filter { -1 == it.value }. map { it.key }
    keysToBeRemoved.forEach { m_EnhancedBookAnatomy.m_SidToIndex.remove(it) }

    keysToBeRemoved = m_EnhancedBookAnatomy.m_EidToIndex.filter { -1 == it.value }. map { it.key }
    keysToBeRemoved.forEach { m_EnhancedBookAnatomy.m_EidToIndex.remove(it) }
  }


  /****************************************************************************/
  /* Flags entries in the indexes as having been handled.  Note that in the
     enhanced file, we process all subverses of a given verse when we hit the
     verse itself.  Hence the forEach below -- this gets rid of all subverses
     of a given verse at one go.

     Note that the Ref.clearS(refKey) call below ought to be redundant, because
     I kinda think I should be called here only with verse refKeys, not with
     subverse ones.  However, I must be wrong about that, because it _is_
     needed.
   */

  private fun done (refKey: RefKey)
  {
    m_EnhancedBookAnatomy.m_SidToIndex[refKey] = -1
    m_EnhancedBookAnatomy.m_EidToIndex[refKey] = -1
  }


  /****************************************************************************/
  private fun error (refKey: RefKey, message: String)
  {
    Logger.error(refKey, message)
  }


  /****************************************************************************/
  /* If dealing with raw text, we need to expand elisions in order to make
     later processing work. */

  private fun expandElisions (document: Document)
  {
    val verseSids = Dom.findNodesByAttributeName(document, "verse", "sid")
    val verseEids = Dom.findNodesByAttributeName(document, "verse", "eid")

    val elisionSids = verseSids.filter { Dom.getAttribute(it, "sid")!!.contains("-") }
    if (elisionSids.isEmpty()) return

    val elisionEids = verseEids.filter { Dom.getAttribute(it, "eid")!!.contains("-") }

    for (i in elisionSids.indices)
    {
      val elisionSid = Dom.getAttribute(elisionSids[i], "sid")!!
      val range = RefRange.rdUsx(elisionSid)
      val changeIdTo = range.getHighAsRef().toString()
      Dom.setAttribute(elisionSids[i], "sid", changeIdTo)
      Dom.setAttribute(elisionEids[i], "eid", changeIdTo)
      val refKeys = range.getAllAsRefKeys()
      for (j in 0 until refKeys.size - 1)
      {
        val id = Ref.rd(refKeys[j]).toString()
        val newSid = Dom.createNode(document, "<verse sid='$id'/>")
        val newEid = Dom.createNode(document, "<verse eid='$id'/>")
        Dom.insertNodeBefore(elisionSids[i], newSid)
        Dom.insertNodeBefore(elisionSids[i], newEid)
      }
    }
  }


  /****************************************************************************/
  /* The present method is called separately to accumulate data for both the
     enhanced text and for the input which went into it.

     When called for the enhanced text, 'rows' will consist of just a single
     element which should correspond to the verse (bear in mind, as explained
     earlier, that although the reversification data sometimes targets subverses,
     they all end up being amalgamated into the owning verse before we reach the
     present processing).

     We can be confident that the enhanced verse does actually exist, because
     checkReversifiedNonPsalms only calls the present method for verses which do
     exist.

     When called for the input text, 'rows' will contain one or more entries.
     There will be a single row if the row was targetting an entire verse, and
     n rows if it (along with other associated rows) was targetting the n
     subverses which go to make up a verse.  In this latter case, the first
     row will hold the reference for subverse zero, and the entries should be
     ordered correctly (I hope).

     The main complicating consideration here is what happens if elements do
     not exist.  As I have just said, this shouldn't be an issue for the
     standardRef, because the present method won't be called if it doesn't.

     It _could_ be an issue for the input data, though.  In fact, I suspect
     it will not be, because I think the reversification processing would
     have failed if it needed input and could not find it.  Just in case,
     though, I insert into the amalgamated text a special marker to identify
     missing inputs.  This will convey useful information and also guarantees
     that when comparing inputs and outputs we will report an error.
  */

  private fun gatherContentForAllConstituents (bookAnatomy: BookAnatomy,
                                               refKeySelector: (ReversificationDataRow) -> RefKey,
                                               rows: List<ReversificationDataRow>): String
  {
    val content = StringBuilder(1000)
    rows.forEach { content.append(gatherContentForSingleConstituent(bookAnatomy, refKeySelector, it)) }
    return content.toString()
  }


  /****************************************************************************/
  /* Another slightly awkward one.

     In most cases we simply want to get the content of the verse (or subverse)
     whose refKey is selected by the refKeySelector method (this will be either
     the sourceRef or the standardRef).

     However, there is one special case where reversification has been applied.
     Sometimes a reversification row targets subverse 2 (ie b) of a verse.
     And sometimes there will not be a reversification row which targets
     subverse 1 (ie a).

     This won't _always_ be the case -- sometimes we _will_ actually have
     something which targets subverse a.

     Where we do not, however, it is because the reversification data simply
     assumes that the original verse itself serves as subverse a, and needs no
     special treatment.  For example, there is a reversification row which
     targets Num 20:28b, but none which targets Num 20:28a.  In this case,
     reversification is simply assuming that the final arrangement will be

       28:20  Some text which now serves as though it were 28:20a
       20:28b Some more text.

     I need to pick up cases where this is the case, because in these I
     need to take text from the input _verse_.

     m_ImplicitReversificationRenumbers contains all the subverse 2 refKeys
     where this is the case.  If we are processing a standard reference and
     it is in m_ImplicitReversificationRenumbers, I need to pick up the
     content of the verse itself as well as the content of the subverse.
   */

  private fun gatherContentForSingleConstituent (bookAnatomy: BookAnatomy, refKeySelector: (ReversificationDataRow) -> RefKey, row: ReversificationDataRow): String
  {
    val key = refKeySelector.invoke(row)
    return if (row.standardRefAsRefKey in m_ImplicitReversificationRenumbers)
      gatherContentForSingleConstituent(bookAnatomy, Ref.clearS(row.standardRefAsRefKey)) + " " + gatherContentForSingleConstituent(bookAnatomy, key)
    else
      gatherContentForSingleConstituent(bookAnatomy, key)
}


  /****************************************************************************/
  private fun gatherContentForSingleConstituent (bookAnatomy: BookAnatomy, refKey: RefKey): String
  {
    if (0L == refKey) return ""

    val sidIx = bookAnatomy.m_SidToIndex[refKey]
    val eidIx = bookAnatomy.m_EidToIndex[refKey]

    return if (null == sidIx)
      "[Missing input: ${Ref.rd(refKey)}]"
    else
      gatherContent(bookAnatomy.m_AllNodes, sidIx, eidIx!!)
  }


  /****************************************************************************/
  /* Gets canonical content. */

  private fun gatherContent (allNodes: List<Node>, sidIx: Int, eidIx: Int): String
  {
    val res = StringBuilder(1000)
    for (i in sidIx .. eidIx) // Normally there's no point in including sid and eid, but I want to press this into service also for canonical titles, where sid and eid are actually just the first and last child of para:d.
    {
      val node = allNodes[i]
      if ("#text" == Dom.getNodeName(node) &&                                // The content is limited to text nodes.
          !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node) &&  // And they're excluded if we know for sure they're non-canonical.
          //!Dom.hasAncestorNamed(node, "para", "d") && // And we need to exclude canonical titles, because these get faffed around with.
          !Dom.hasAttribute(node, "_X_wasCanonicalTitle"))       // And by the same token, we need to exclude things which _were_ canonical titles, but have had to be changed to plain-ish text
      {
        res.append(" ")
        res.append(node.textContent)
      }
    }

    return res.toString()
  }


  /****************************************************************************/
  /* Does what it says on the tin. */

  private fun gatherContentForCanonicalTitle (titleNode: Node): String
  {
    val res = StringBuilder(500)
    Dom.findAllTextNodes(titleNode)
      .filter { !Usx.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
      .forEach { res.append(" "); res.append(it.textContent) }

    return res.toString()
  }


  /****************************************************************************/
  /* Sets up mappings as described in getMappings for each of the books listed
     in source books.  Note that we do have to cater here for the possibility
     we have been asked for a book which does not exist: processing above
     always assumes that if we are checking enhanced book xyz that a source
     book exists for xyz.  However, reversification may sometimes create new
     books, so this is not always the case. */

  private fun getAllSourceMappings (sourceBookNumbers: Set<Int>)
  {
    fun getMappings (bookNumber: Int)
    {
      val filePath = BibleBookAndFileMapperRawUsx.getFilePathForBook(bookNumber) ?: return
      val document = Dom.getDocument(filePath)
      m_RawBookAnatomies[bookNumber] = getBookAnatomy(document)
    }

    m_RawBookAnatomies.clear()
    sourceBookNumbers.forEach { getMappings(it) }
  }


  /****************************************************************************/
  /* Gets a list of all nodes in the document, and sets up two maps which map
     the sid of sid nodes to indexes into this list, and the eid of eid
     nodes -- plus a map relating chapter sids to para:d (canonical title) where
     appropriate. */

  private fun getBookAnatomy (document: Document): BookAnatomy
  {
    /**************************************************************************/
    //Dbg.outputDom(document, "a")



    /**************************************************************************/
    sidify(document)
    introduceDummyEidsIfNecessary(document)
    val res = BookAnatomy()
    expandElisions(document)
    res.m_AllNodes = Dom.collectNodesInTree(document)



    /**************************************************************************/
    for (i in res.m_AllNodes.indices)
    {
      val node = res.m_AllNodes[i]
      when (Dom.getNodeName(node))
      {
        "verse" ->
        {
          if (Dom.hasAttribute(node, "sid"))
            res.m_SidToIndex[Ref.rdUsx(Dom.getAttribute(node, "sid")!!).toRefKey()] = i
          else
            res.m_EidToIndex[Ref.rdUsx(Dom.getAttribute(node, "eid")!!).toRefKey()] = i
        }

        "para" ->
        {
          val style = Dom.getAttribute(node, "style")
          if (null != style && "d" == style && "start" == Dom.getAttribute(node, "_X_location"))
          {
            val sidRefKey = Ref.rdUsx(Dom.getAttribute(Dom.getAncestorNamed(node, "_X_chapter")!!, "sid")!!).toRefKey()
            res.m_chapterSidToPsalmTitle[sidRefKey] = node
          }
        }
      } // when
    } // for



    /**************************************************************************/
    return res
  }


  /****************************************************************************/
  /* The last statement below was originally based upon bcv, rather than bcvs,
     and therefore, where we were targetting subverses of a verse, collected
     together all of the rows for the subverses of that verse.  Changes since
     then mean that in fact I don't want to do that -- I need to process each
     subverse individually, and the change to bcvs does just that, but does
     so in a rather redundant manner, because all I really need is a list of
     individual rows, and to process each one separately.

     I'm retaining things as below pro tem because changing from a map to a list
     has a fair number of knock-on effects, and since psalm title processing
     also goes through the affected methods and _may_ still require a map,
     I don't want to upset the apple cart too much. */
  private fun getReversificationDetails (enhancedBookNumber: Int): ReversificationDetails
  {
    val res = ReversificationDetails()
    res.m_AllRows = m_ReversificationRowsForAllBooks.filter { enhancedBookNumber == Ref.getB(it.standardRefAsRefKey) }
    res.m_CanonicalTitleRows = res.m_AllRows.filter { 0 != it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle.or(ReversificationData.C_StandardIsPsalmTitle)) }
    val nonTitleRows = res.m_AllRows.filter { 0 == it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle.or(ReversificationData.C_StandardIsPsalmTitle)) }
    res.m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows = nonTitleRows.groupBy { it.standardRef.toRefKey_bcvs() } // See note in head-of-method comments.
    return res
  }


  /****************************************************************************/
  /* Raw USX may not have verse eids.  Validation requires that we know what
     canonical text comes within verses, to which end I introduce dummy eids
     if the document doesn't have any, placing them immediately before the
     following sid. */

  private fun introduceDummyEidsIfNecessary (document: Document)
  {
    /**************************************************************************/
    if (null != Dom.findNodeByAttributeName(document, "verse", "eid")) return



    /**************************************************************************/
    var prevVerseSidNode: Node? = null



    /**************************************************************************/
    fun makeEid (eid: String): Node
    {
      return Dom.createNode(document, "<verse eid='$eid'/>")
    }


    /**************************************************************************/
    fun processNode (node: Node)
    {
      when (Dom.getNodeName(node))
      {
        "chapter" ->
        {
          if (null != prevVerseSidNode)
          {
            val eid = makeEid(Dom.getAttribute(prevVerseSidNode!!, "sid")!!)
            Dom.insertNodeBefore(node, eid)
            prevVerseSidNode = null
          }
        }

        "verse" ->
        {
          if (null != prevVerseSidNode)
          {
            val eid = makeEid(Dom.getAttribute(prevVerseSidNode!!, "sid")!!)
            Dom.insertNodeBefore(node, eid)
          }

          prevVerseSidNode = node
        }
      }
    }


    /**************************************************************************/
    Dom.collectNodesInTree(document).forEach { processNode(it) }
    Dom.findNodeByName(document, "usx")!!.appendChild(makeEid(Dom.getAttribute(prevVerseSidNode!!, "sid")!!))
 }


  /****************************************************************************/
  private class BookAnatomy
  {
    lateinit var m_AllNodes: List<Node>
    var m_SidToIndex: MutableMap<RefKey, Int> = HashMap(2000)
    var m_EidToIndex: MutableMap<RefKey, Int> = HashMap(2000)
    var m_chapterSidToPsalmTitle: MutableMap<RefKey, Node> = HashMap(200)
  }


  /****************************************************************************/
  /* Note, as regards m_NonTitleRowGroups that there will be one entry for each
     reversification standard verse.  If the verse is made up of subverses,
     then the value associated with the standard verse will a list of rows,
     each contributing to the content of that verse. */

  private class ReversificationDetails
  {
    lateinit var m_AllRows: List<ReversificationDataRow>                        // All reversification rows for the current enhanced text, in order.
    lateinit var m_CanonicalTitleRows: List<ReversificationDataRow>             // All rows for the current enhanced text which contain 'Title' in source or standard ref, in order.
    lateinit var m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows: Map<RefKey, List<ReversificationDataRow>> // Maps enhanced refKey to all of the rows involved in producing it.
  }


  /****************************************************************************/
  private lateinit var m_EnhancedBookAnatomy: BookAnatomy
  private var m_RawBookAnatomies: MutableMap<Int, BookAnatomy> = HashMap()

  private lateinit var m_EnhancedBookAbbreviation: String
  private lateinit var m_ReversificationRowsForAllBooks: List<ReversificationDataRow>

  private lateinit var m_ImplicitReversificationRenumbers: Set<RefKey>  // See head-of-method comments for the method which uses this.
}
