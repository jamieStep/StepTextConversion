package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.contains
import org.stepbible.textconverter.support.miscellaneous.get
import org.stepbible.textconverter.support.miscellaneous.getAllNodes
import org.stepbible.textconverter.support.ref.Ref
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.utils.*
import org.w3c.dom.Node

/******************************************************************************/
/**
* Compares the canonical content of two texts.
*
* I *don't* check anything other than canonical content -- I don't check
* headings, footnotes, cross-references etc.  In fact this is probably the
* major shortcoming of what I do here, because we have very specific
* requirements regarding annotation when reversification is applied, and I
* should probably check that they have been satisfied.  However, that these
* requirements are currently not all they will be very difficult to validate.
*
* Note that by contrast with earlier version of this code, I don't do any
* tidying up ahead of performing the comparison: I rely upon the caller to
* have expanded elisions, and (where one of the structures is USX-based) to
* have created sids and eids in preference to numbers if necessary.
*
* @author ARA 'Jamie' Jamieson
*/

object ContentValidator
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Carries out content checks.
   *
   * @param dataCollectionNew
   * @param fileProtocolNew
   * @param dataCollectionOld
   * @param fileProtocolOld
   *
   */

  fun process (dataCollectionNew: X_DataCollection, fileProtocolNew: X_FileProtocol,
               dataCollectionOld: X_DataCollection, fileProtocolOld: X_FileProtocol)
  {
//    Dbg.d(dataCollectionOld.getDocument(), "a.xml")
//    Dbg.d(dataCollectionNew.getDocument(), "b.xml")

    m_DataCollectionNew = dataCollectionNew
    m_DataCollectionOld = dataCollectionOld
    m_FileProtocolNew = fileProtocolNew
    m_FileProtocolOld = fileProtocolOld
    
    Dbg.reportProgress("Validating canonical content")

    m_ImplicitReversificationRenumbers = ReversificationData.getImplicitRenumbers()
    m_ReversificationRowsForAllBooks = ReversificationData.getAllAcceptedRows()
    dataCollectionNew.getBookNumbers().forEach { contentValidationForBook(it) }

    dataCollectionNew.getProcessRegistry().iHaveDone(this, listOf(ProcessRegistry.DetailedContentValidated))
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun contentValidationForBook (bookNo: Int)
  {
   /**************************************************************************/
   if (null == m_DataCollectionNew.getRootNode(bookNo)) return
   Dbg.reportProgress("Checking " + BibleBookNamesUsx.numberToAbbreviatedName(bookNo))
   //Dbg.outputDom(m_DataCollectionNew.getRootNode(bookNo)!!.ownerDocument)



    /**************************************************************************/
    /* Get the basic data structures with which we need to work. */

    m_BookAnatomyNew = getBookAnatomy(m_DataCollectionNew.getRootNode(bookNo)!!, m_FileProtocolNew) // Maps relating sid refKeys to sid nodes, etc.
    val reversificationDetails = getReversificationDetails(bookNo) // All of the reversification rows which target this book.
    val sourceBookNumbers = listOf(bookNo) union reversificationDetails.m_AllRows.map { Ref.getB(it.sourceRefAsRefKey) }.toMutableSet() // This book itself (needed where we're not doing reversification), plus all the books which feed into this book under reversification.
    m_RawBookAnatomies.clear()
    sourceBookNumbers.forEach { oldBookNo ->
      if (null != m_DataCollectionOld.getRootNode(oldBookNo)) // Need to cater for the possibility that reversification may create new books for which there is no old counterpart.
        m_RawBookAnatomies[oldBookNo] = getBookAnatomy(m_DataCollectionOld.getRootNode(oldBookNo)!!, m_FileProtocolOld)
    }



    /**************************************************************************/
    /* The order here matters, because later processing relies upon earlier
       processing having removed 'awkward' verses from the list of those still
       to be handled.  We need worry about the implications of reversification
       only where we are actually restructuring the text here, which means only
       of we are applying conversion-time reversification.*/

    if ("conversiontime" == ConfigData["stepReversificationType"]!!.lowercase())
    {
      checkReversifiedEverythingButCanonicalTitles(reversificationDetails)
      checkReversifiedCanonicalTitles(reversificationDetails)
    }



    /**************************************************************************/
    /* Deals with anything not affected by conversion-time reversification
       (which, on a run where we are not applying reversification, or where we
       are applying run-time reversification), means that the following deals
       with everything). */

    checkNonReversified(bookNo)
    checkResidual()
  }





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

  private fun checkNonReversified (bookNo: Int)
  {
    /**************************************************************************/
    val anatomyOld = m_RawBookAnatomies[bookNo] ?: return



    /**************************************************************************/
    fun check (refKey: RefKey)
    {
      /************************************************************************/
      //Dbg.d("Gen 2:2", Ref.rd(refKey).toString())



      /************************************************************************/
      val newSid = m_BookAnatomyNew.m_SidToIndex[refKey]!!
      val newEid = m_BookAnatomyNew.m_EidToIndex[refKey]!!



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

      var skipTest = newSid == -1

      if (!skipTest)
        skipTest = m_FileProtocolNew.isDummySid(m_BookAnatomyNew.m_AllNodes[newSid]) // Ignore dummy verses.

      if (!skipTest) // Verses which didn't exist in the original (presumably ones which were part of an elision).
      {
        val sid = m_BookAnatomyNew.m_AllNodes[newSid]
        skipTest = NodeMarker.hasElisionType(sid) && !NodeMarker.hasMasterForElisionOfLength(sid) // It's an elided verse, but not the master.
      }

      if (!skipTest)
        skipTest = Dom.hasAttribute(m_BookAnatomyNew.m_AllNodes[newSid], "_X_reasonEmpty") // Verses flagged as legitimately empty.

      if (!skipTest)
        skipTest = "tableElision" == NodeMarker.getEmptyVerseType(m_BookAnatomyNew.m_AllNodes[newSid])



      /************************************************************************/
      /* We always want to flag that we've checked this verse, even if, in fact,
         we're not applying any checks. */

      done(refKey)
      if (skipTest) return



      /************************************************************************/
      /* We're always interested in the content of a single enhanced verse, so
         that much is straightforward. */

      val contentNew = gatherContent(m_BookAnatomyNew.m_AllNodes, newSid, newEid, m_FileProtocolNew)



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

      val sidNodeNew = m_BookAnatomyNew.m_AllNodes[newSid]
      val sidRefNewAsString = if (Dom.hasAttribute(sidNodeNew, "_X_originalId")) Dom.getAttribute(sidNodeNew, "_X_originalId")!! else m_FileProtocolNew.getSid(sidNodeNew)

      val lowRefKey: RefKey
      val highRefKey: RefKey

      if ("-" in sidRefNewAsString)
      {
        val rng = m_FileProtocolNew.readRefCollection(sidRefNewAsString)
        lowRefKey = rng.getLowAsRefKey()
        highRefKey = rng.getHighAsRefKey()
      }
      else
      {
        lowRefKey = m_FileProtocolNew.readRef(sidRefNewAsString).toRefKey()
        highRefKey = lowRefKey
      }

      var inputContent = ""

      for (i in lowRefKey .. highRefKey)
      {
        val rawSid = anatomyOld.m_SidToIndex[i] ?: continue
        val rawEid = anatomyOld.m_EidToIndex[i]!!
        inputContent += " " + gatherContent(anatomyOld.m_AllNodes, rawSid, rawEid, m_FileProtocolOld)
      }

      compare(contentNew, inputContent, refKey, refKey, getAssociatedHeaderMaterial(sidNodeNew))
    } // fun

    m_BookAnatomyNew.m_SidToIndex.keys.forEach { check(it) }
    done()
  }


  /****************************************************************************/
  /* Looks for any remaining verses we haven't dealt with. */

  private fun checkResidual ()
  {
    fun process (refKey: RefKey)
    {
      val ix = m_BookAnatomyNew.m_SidToIndex[refKey]!!
      if (NodeMarker.C_Dummy !in m_BookAnatomyNew.m_AllNodes[ix])
      {
        //Dbg.d(m_EnhancedBibleAnatomy.m_AllNodes[ix].ownerDocument)
        error(refKey, "Verse not covered by validation.")
      }
    }

    m_BookAnatomyNew.m_SidToIndex.keys.forEach { process(it) }
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
      .filter { it.sourceIsPsalmTitle() }
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
    val bookAnatomyOld = m_RawBookAnatomies[Ref.getB(row.sourceRefAsRefKey)]!!
    val paraOld = bookAnatomyOld.m_chapterSidToPsalmTitle[row.sourceRefAsRefKey]!!
    val paraNew = m_BookAnatomyNew.m_chapterSidToPsalmTitle[row.standardRefAsRefKey]!!
    val contentOld = gatherContentForCanonicalTitle(paraOld, m_FileProtocolOld)
    val contentNew = gatherContentForCanonicalTitle(paraNew, m_FileProtocolNew)
    compare(contentNew, contentOld, row.standardRefAsRefKey, row.sourceRefAsRefKey, "")
    // No need to call 'done' here because done is concerned only with verses, and here we are effectively dealing with a chapter.
  }


  /****************************************************************************/
  /* Handles the case where we are turning one, or perhaps two, input verses
     into a canonical title. */

  private fun checkReversifiedCanonicalTitles_VersesToTitle (owningStandardRefKey: RefKey, rows: List<ReversificationDataRow>)
  {
    Dbg.d(owningStandardRefKey.toString())
    fun getInputRefKeyUsingSourceField   (row: ReversificationDataRow): RefKey { return row.sourceRefAsRefKey }

    val paraNew = m_BookAnatomyNew.m_chapterSidToPsalmTitle[owningStandardRefKey]!!
    val contentNew = gatherContentForCanonicalTitle(paraNew, m_FileProtocolNew)

    val bibleAnatomyOld = m_RawBookAnatomies[Ref.getB(rows[0].sourceRefAsRefKey)]!!
    val contentOld = gatherContentForAllConstituents(bibleAnatomyOld, ::getInputRefKeyUsingSourceField, rows, m_FileProtocolOld)

    compare(contentNew, contentOld, owningStandardRefKey, rows[0].sourceRefAsRefKey, "")
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
        val sidNode = m_BookAnatomyNew.m_AllNodes[m_BookAnatomyNew.m_SidToIndex[theRows[0].standardRefAsRefKey]!!]
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

      val refKeySelector = if (rows[0].isRenumber()) ::getInputRefKeyUsingSourceField else ::getInputRefKeyUsingStandardField
      val inputBookAnatomy = m_RawBookAnatomies[Ref.getB(refKeySelector(rows[0]))]

      val contentOld = if (rows[0].action.contains("merge")) "" else gatherContentForAllConstituents(inputBookAnatomy!!, refKeySelector, rows, m_FileProtocolOld)
      val contentNew = gatherContentForAllConstituents(m_BookAnatomyNew, ::getInputRefKeyUsingStandardField, listOf(rows[0]), m_FileProtocolNew)

      if (contentNew.startsWith("[Missing"))
      {
        if (!rows[0].standardRef.hasS())
        {
          val message = "Missing verse: '${rows[0].standardRef}'"
          error(rows[0].standardRefAsRefKey, message)
        }
      }
      else
        compare(contentNew, contentOld, rows[0].standardRefAsRefKey, rows[0].sourceRefAsRefKey, "")

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

  private fun compare (enhanced: String, input: String, enhancedRefKey: RefKey, inputRefKey: RefKey, associatedHeaderContent: String)
  {
    /**************************************************************************/
    /* Forgotten what this means.  I _think_ I was using it to mark places
       where I reckoned the situation was too complicated for validation to be
       feasible. */

    if ('\u0001' in enhanced) return



    /**************************************************************************/
    val contentEnhanced = enhanced.replace("&#160;".toRegex(), " ") // XML non-breaking space.
                                  .replace("&#x2013;".toRegex(), " ")  // En-dash.
                                  .replace("&nbsp;", " ")
                                  .replace("\u00a0", " ") // Unicode non-breaking space.
                                  .replace("\\s+".toRegex(), " ").trim()

    val contentInput = input.replace("&#160;".toRegex(), " ") // XML non-breaking space.
                            .replace("&#x2013;".toRegex(), " ") // En-dash.
                            .replace("&nbsp;", " ")
                            .replace("\u00a0", " ") // Unicode non-breaking space.
                            .replace("\\s+".toRegex(), " ").trim()

    if (contentInput.replace("\\s+".toRegex(), "") == contentEnhanced.replace("\\s+".toRegex(), "")) return



    /**************************************************************************/
    /* This is getting too complicated to explain.  Some psalm title checking
       is driven by what reversification does, and in those cases, I will
       already have arranged things above such that the test we have just
       carried out is enough.

       But there are some cases where, irrespective of what reversification
       might entail, I may have been forced to rearrange things myself.  In
       these cases, I will have separate title and v1, but may have not done
       so in the original text.  And in these cases, I will have the content of
       the associated header in associatedHeaderContent, and will need to try
       prepending that to the text I am checking. */

    if (associatedHeaderContent.isNotEmpty())
    {
      val associatedHeader = associatedHeaderContent.replace("&#160;".toRegex(), " ") // XML non-breaking space.
                                                    .replace("&#x2013;".toRegex(), " ") // En-dash.
                                                    .replace("&nbsp;", " ")
                                                    .replace("\u00a0", " ") // Unicode non-breaking space.
                                                    .replace("\\s+".toRegex(), " ").trim()
      if (contentInput.replace("\\s+".toRegex(), "") == (associatedHeader + contentEnhanced).replace("\\s+".toRegex(), "")) return
    }


    /**************************************************************************/
    val message = "Verse mismatch:<nl>  Original = '$contentInput'<nl>     Final = '$contentEnhanced'<nl>"
    error(enhancedRefKey, message)
  }


  /****************************************************************************/
  /* Removes from the mapping structures those items which we flagged previously
     for deletion. */

  private fun done ()
  {
    var keysToBeRemoved = m_BookAnatomyNew.m_SidToIndex.filter { -1 == it.value }. map { it.key }
    keysToBeRemoved.forEach { m_BookAnatomyNew.m_SidToIndex.remove(it) }

    keysToBeRemoved = m_BookAnatomyNew.m_EidToIndex.filter { -1 == it.value }. map { it.key }
    keysToBeRemoved.forEach { m_BookAnatomyNew.m_EidToIndex.remove(it) }
  }


  /****************************************************************************/
  /* Flags entries in the indexes as having been handled.  Note that in the
     enhanced file, we process all subverses of a given verse when we hit the
     verse itself.  Hence the forEach below -- this gets rid of all subverses
     of a given verse at one go.

     Note that the Ref.clearS(refKey) call below ought to be redundant, because
     I kinda think I should be called here only with verse refKeys, not with
     subverse ones.  However, I must be wrong about that, because it _is_
     needed. */

  private fun done (refKey: RefKey)
  {
    m_BookAnatomyNew.m_SidToIndex[refKey] = -1
    m_BookAnatomyNew.m_EidToIndex[refKey] = -1
  }


  /****************************************************************************/
  /* Gets canonical content. */

  private fun gatherContent (allNodes: List<Node>, sidIx: Int, eidIx: Int, fileProtocol: X_FileProtocol): String
  {
    val res = StringBuilder(1000)
    for (i in sidIx .. eidIx) // Normally there's no point in including sid and eid, but I want to press this into service also for canonical titles, where sid and eid are actually just the first and last child of para:d.
    {
      val node = allNodes[i]
      if ("#text" == Dom.getNodeName(node) &&                                         // The content is limited to text nodes.
          !fileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(node))    // And by the same token, we need to exclude things which _were_ canonical titles, but have had to be changed to plain-ish text
      {
        res.append(" ")
        res.append(node.textContent)
      }
    }

    return res.toString()
  }


  /****************************************************************************/
  /* In Psalms only (and even then, only on v1) looks to see if the chapter has
     a canonical title.  If it does, returns the content of the title. */

  private fun getAssociatedHeaderMaterial (v1Sid: Node): String
  {
    val sidRef = m_FileProtocolNew.readRef(v1Sid[m_FileProtocolNew.attrName_verseSid()]!!)
    if (1 != sidRef.getV() || "Psa" != BibleBookNamesUsx.numberToAbbreviatedName(sidRef.getB())) return ""

    var res = ""
    val chapterNode = Dom.findAncestorByNodeName(v1Sid, m_FileProtocolNew.tagName_chapter())!!
    val canonicalHeaderNode = Dom.getNodesInTree(chapterNode).firstOrNull { m_FileProtocolNew.isCanonicalTitleNode(it) } ?: return ""

    Dom.getNodesInTree(canonicalHeaderNode).forEach {
      if (m_FileProtocolNew.isCanonicalNode(it) && Dom.isTextNode(it))
        res += it.textContent
    }

    return res
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
                                               rows: List<ReversificationDataRow>,
                                               fileProtocol: X_FileProtocol): String
  {
    val content = StringBuilder(1000)
    rows.forEach { content.append(gatherContentForSingleConstituent(bookAnatomy, refKeySelector, it, fileProtocol)) }
    return content.toString()
  }


  /****************************************************************************/
  /* Does what it says on the tin. */

  private fun gatherContentForCanonicalTitle (titleNode: Node, fileProtocol: X_FileProtocol): String
  {
    val res = StringBuilder(500)
    Dom.findAllTextNodes(titleNode)
      .filter { !fileProtocol.isInherentlyNonCanonicalTagOrIsUnderNonCanonicalTag(it) }
      .forEach { res.append(" "); res.append(it.textContent) }

    return res.toString()
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

  private fun gatherContentForSingleConstituent (bookAnatomy: BookAnatomy,
                                                 refKeySelector: (ReversificationDataRow) -> RefKey,
                                                 row: ReversificationDataRow,
                                                 fileProtocol: X_FileProtocol): String
  {
    val key = refKeySelector.invoke(row)
    return if (row.standardRefAsRefKey in m_ImplicitReversificationRenumbers)
      gatherContentForSingleConstituent(bookAnatomy, Ref.clearS(row.standardRefAsRefKey), fileProtocol) + " " + gatherContentForSingleConstituent(bookAnatomy, key, fileProtocol)
    else
      gatherContentForSingleConstituent(bookAnatomy, key, fileProtocol)
}


  /****************************************************************************/
  private fun gatherContentForSingleConstituent (bookAnatomy: BookAnatomy, refKey: RefKey, fileProtocol: X_FileProtocol): String
  {
    if (0L == refKey) return ""

    val sidIx = bookAnatomy.m_SidToIndex[refKey]
    val eidIx = bookAnatomy.m_EidToIndex[refKey]

    return if (null == sidIx)
      "[Missing input: ${Ref.rd(refKey)}]"
    else
      gatherContent(bookAnatomy.m_AllNodes, sidIx, eidIx!!, fileProtocol)
  }


  /****************************************************************************/
  /* Gets a list of all nodes in the document, and sets up two maps which map
     the sid of sid nodes to indexes into this list, and the eid of eid
     nodes -- plus a map relating chapter sids to canonical title where
     appropriate. */

  private fun getBookAnatomy (rootNode: Node, fileProtocol: X_FileProtocol): BookAnatomy
  {
    /**************************************************************************/
    //Dbg.outputDom(rootNode.ownerDocument)



    /**************************************************************************/
    val res = BookAnatomy()
    res.m_AllNodes = rootNode.getAllNodes()



    /**************************************************************************/
    var mostRecentSidRefKey = Ref.rd(0, 0, 0, 0).toRefKey()
    for (i in res.m_AllNodes.indices)
    {
      val node = res.m_AllNodes[i]
      if (fileProtocol.tagName_verse() == Dom.getNodeName(node))
      {
        //Dbg.dCont(Dom.toString(node), ".2.2")
        if (fileProtocol.attrName_verseSid() in node)
        {
          //mostRecentSidRefKey = fileProtocol.readRef(node[fileProtocol.attrName_verseSid()]!!).toRefKey() $$$$
          mostRecentSidRefKey = fileProtocol.readRefCollection(node[fileProtocol.attrName_verseSid()]!!).getLastAsRefKey()
          res.m_SidToIndex[mostRecentSidRefKey] = i
        }
        else
          res.m_EidToIndex[fileProtocol.readRefCollection(node[fileProtocol.attrName_verseEid()]!!).getLastAsRefKey()] = i
      }

      else if (fileProtocol.isCanonicalTitleNode(node))
      {
        val sidRefKey = fileProtocol.readRef(Dom.getAttribute(Dom.getAncestorNamed(node, fileProtocol.tagName_chapter())!!, fileProtocol.attrName_chapterSid())!!).toRefKey()
        res.m_chapterSidToPsalmTitle[sidRefKey] = node
      }
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

  private fun getReversificationDetails (bookNo: Int): ReversificationDetails
  {
    val res = ReversificationDetails()

    if ("conversiontime" != ConfigData["stepReversificationType"]!!)
      return res

    // All the reversification rows which target this book.
    res.m_AllRows = m_ReversificationRowsForAllBooks.filter { bookNo == Ref.getB(it.standardRefAsRefKey) }


    // Canonical title rows for this book.
    res.m_CanonicalTitleRows = res.m_AllRows.filter { 0 != it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle.or(ReversificationData.C_StandardIsPsalmTitle)) }


    // Anything _other_ than canonical title rows.
    val nonTitleRows = res.m_AllRows.filter { 0 == it.processingFlags.and(ReversificationData.C_SourceIsPsalmTitle.or(ReversificationData.C_StandardIsPsalmTitle)) }


    // Non-title rows grouped by standard ref.  This caters for the situation where a number of inputs are amalgamated to form a single verse.
    res.m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows = nonTitleRows.groupBy { it.standardRef.toRefKey_bcvs() } // See note in head-of-method comments.

    return res
  }


  /****************************************************************************/
  private fun error (refKey: RefKey, message: String)
  {
    Logger.error(refKey, message)
  }


  /****************************************************************************/
  private class BookAnatomy
  {
    lateinit var m_AllNodes: List<Node>
    var m_SidToIndex: MutableMap<RefKey, Int> = mutableMapOf()
    var m_EidToIndex: MutableMap<RefKey, Int> = mutableMapOf()
    var m_chapterSidToPsalmTitle: MutableMap<RefKey, Node> = mutableMapOf()
  }


  /****************************************************************************/
  /* Note, as regards m_NonTitleRowGroups that there will be one entry for each
     reversification standard verse.  If the verse is made up of subverses,
     then the value associated with the standard verse will a list of rows,
     each contributing to the content of that verse. */

  private class ReversificationDetails
  {
    var m_AllRows: List<ReversificationDataRow> = listOf()                       // All reversification rows for the current enhanced text, in order.
    var m_CanonicalTitleRows: List<ReversificationDataRow> = listOf()            // All rows for the current enhanced text which contain 'Title' in source or standard ref, in order.
    var m_MapNonTitleItemsStandardRefKeyToAssociatedReversificationRows: Map<RefKey, List<ReversificationDataRow>> = mutableMapOf() // Maps enhanced refKey to all of the rows involved in producing it.
  }


  /****************************************************************************/
  private lateinit var  m_DataCollectionNew: X_DataCollection
  private lateinit var  m_DataCollectionOld: X_DataCollection
  private lateinit var m_FileProtocolNew: X_FileProtocol
  private lateinit var m_FileProtocolOld: X_FileProtocol

  private lateinit var m_BookAnatomyNew: BookAnatomy
  private var m_RawBookAnatomies: MutableMap<Int, BookAnatomy> = mutableMapOf()
  private lateinit var m_ImplicitReversificationRenumbers: Set<RefKey>
  private lateinit var m_ReversificationRowsForAllBooks: List<ReversificationDataRow>
}