package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.ref.Ref
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructure
import org.stepbible.textconverter.protocolagnosticutils.PA_MissingVerseHandler
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleStructureOsis2ModScheme
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithoutStackTraceAbandonRun

/*******************************************************************************/
/**
 * This does several things.  Logically they are distinct, and I would prefer to
 * keep them separate, but unfortunately they need to happen together in order
 * for the processing as a whole to work.  So, not necessarily in the order in
 * which they are carried out:
 *
 * - It checks to see if there are any holes in the versification, and if so,
 *   arranges to create empty verses to fill them.  Note that it looks only
 *   for holes at the start of a chapter or in the middle of it, not at the end.
 *   This processing is needed in order to for osis2mod to work properly.
 *
 * - On non-reversification runs (ie runs where we are implying that the text
 *   conforms to a user-selected Crosswire versification scheme), it deletes
 *   any books which are outside the scope of that scheme.
 *
 * - Again on non-reversification runs, it checks whether any verses are out of
 *   order.  (Very occasionally translators deliberately supply verses out of
 *   order.  Our own processing can handle that, but the Crosswire version of
 *   osis2mod, which we invoke on non-reversification runs, cannot.)
 *
 * - And again on non-reversification runs, it checks that after all of this
 *   processing, the text is reasonably well aligned with the selected Crosswire
 *   versification scheme.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_BasicAlignerToScheme: ObjectInterface
{
  /****************************************************************************/
  /* Checks for some basic things which may be adrift (like the text
     containing subverses or lacking verses).  Depending upon the circumstances,
     some of these we may be able to remedy; others we may simply have to
     report, and abandon further processing. */

  fun process (dataCollection: X_DataCollection)
  {
    Rpt.report(1, "Performing structural validation and correction (no separate per-book reporting for this step).")
    structuralValidationAndCorrection1(dataCollection)

    Rpt.report(1,"Checking for missing verses (no separate per-book reporting for this step).")
    validationForOrderingAndHoles1(dataCollection)
  }


  /****************************************************************************/
  private fun structuralValidationAndCorrection1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    val bibleStructure = dataCollection.getBibleStructure()



    /**************************************************************************/
    /* Part 1 is to work out which option we are dealing with.  If we are
       reversifying, we won't be working to a particular versification scheme.
       If we are _not_ reversifying, then a suitable scheme must have been
       specified and we need to get hold of it. */

    var targetScheme: BibleStructureOsis2ModScheme? = null
    var targetSchemeName = ""
    val reversificationType = ConfigData["stepReversificationType"]!!.lowercase()
    if ("none" == reversificationType)
    {
      val selectedSchemeName = ConfigData["stepVersificationScheme"] ?: throw StepExceptionWithoutStackTraceAbandonRun("Non-reversification run, but no Crosswire versification scheme identified.")
      targetScheme = BibleStructure.makeOsis2modSchemeInstance(selectedSchemeName)
      targetSchemeName = selectedSchemeName
    }



    /**************************************************************************/
    /* If we have a specific scheme in mind, we can't have any books which are
       not in that scheme.  If we find such books, therefore, we drop them and
       report them. */

    if (null != targetScheme)
    {
      val deletedBooks: MutableList<String> = mutableListOf()

      dataCollection.getBookNumbers().forEach {bookNumber ->
        if (!targetScheme.bookExists(bookNumber))
        {
          val badRootNode = dataCollection.getRootNodes()[bookNumber]
          Dom.deleteNode(badRootNode)
          dataCollection.removeNodeFromRootNodeStructure(bookNumber)
          deletedBooks.add(dataCollection.getFileProtocol().getBookAbbreviation(badRootNode))
          return@forEach
        }
      }



      /************************************************************************/
      if (deletedBooks.isNotEmpty())
        ConfigData["stepDeletedBooks"] = deletedBooks.joinToString(", ")
    }



    /**************************************************************************/
    /* If we're not restructuring, it's ok to have missing verses at this point,
       but we must now fill them all in. */

    if (PA_MissingVerseHandler(dataCollection.getFileProtocol()).createEmptyVersesForMissingVerses(dataCollection))
      dataCollection.reloadBibleStructureFromRootNodes(true)



    /**************************************************************************/
    /* If we're not reversifying, we need to be reasonably well aligned with
       one of the Crosswire versification schemes.

       At one time I regarded it as an error if the target scheme made no
       provision for a verse present in the supplied text.  However, on a
       non-Samification run we _have_ to use a Crosswire scheme even if
       it's not an ideal fit, so I've had to reduce most things here
       to a warning. */

    if ("runtime" != reversificationType)
    {
      dataCollection.getBookNumbers().forEach { bookNo ->
        val comparison = BibleStructure.compareWithGivenScheme(bookNo, bibleStructure, targetScheme!!)
        comparison.chaptersInTargetSchemeButNotInTextUnderConstruction.forEach { refKey -> Logger.warning(refKey, "Chapter in $targetSchemeName but not in supplied text.") }
        comparison.chaptersInTextUnderConstructionButNotInTargetScheme.forEach { refKey -> Logger.error  (refKey, "Chapter in supplied text but not in $targetSchemeName.") }
        comparison.versesInTextUnderConstructionButNotInTargetScheme  .forEach { refKey -> Logger.warning(refKey, "Verse in supplied text but not in $targetSchemeName.") }
        comparison.versesInTextUnderConstructionOutOfOrder            .forEach { refKey -> Logger.warning (refKey, "Verse out of order.") }

        val missingVerses = comparison.versesInTargetSchemeButNotInTextUnderConstruction.toSet()
        val commonlyMissingVerses = BibleAnatomy.getCommonlyMissingVerses().toSet()
        val unexpectedMissings = missingVerses - commonlyMissingVerses
        val expectedMissings = missingVerses intersect commonlyMissingVerses

        val unexpectedMissingsAsString = if (unexpectedMissings.isEmpty()) "" else "The text lacks the following verse(s) which the target versification scheme expects: " + unexpectedMissings.sorted().joinToString(", "){ Ref.rd(it).toString("bcv") }
        val expectedMissingsAsString   = if (expectedMissings.isEmpty())   "" else "The text lacks the following verse(s) which the target versification scheme expects.  However, many texts lack these verses, so this is not of particular concern.: " + expectedMissings.sorted().joinToString(", "){ Ref.rd(it).toString() }
        val msg = (unexpectedMissingsAsString + expectedMissingsAsString).trim()
        if (msg.isNotEmpty())
           Logger.info(msg)
      }
    }


    /**************************************************************************/
    Logger.announceAll(true)
  }


  /****************************************************************************/
  private fun validationForOrderingAndHoles1 (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    /* There's a specific config parameter which says if out-of-order verses
       should be reported as an error.  Otherwise it's just a warning.

       FWIW, our own software (targeted at the 'STEP' audience) can cope; the
       Crosswire variant cannot: if presented with out-of-order verses, it
       simply reorders them, and if the translators have deliberately put them
       out of order (which occasionally they do) then that's a problem. */

    val outOfOrderVerses = dataCollection.getBibleStructure().getOutOfOrderVerses() // I think this will cover chapters too.
    if (outOfOrderVerses.isNotEmpty())
    {
      // Dbg.outputDom(dataCollection.getRootNode(24)!!.ownerDocument  )
      val reporter: (String) -> Unit = if (ConfigData.getAsBoolean("stepValidationReportOutOfOrderAsError", "y")) Logger::error else Logger::warning
      reporter("Locations where verses are out of order: " + outOfOrderVerses.joinToString(", "){ Ref.rd(it).toString() })
    }



    /**************************************************************************/
    /* I'm not sure how much out-of-order books matter.  Crosswire will
       definitely simply reorder them.  I don't know about our software.
       Everything should still _work_ either way -- the worst that should
       happen is that the text doesn't follow the required order.  I'm going
       to treat this as just a warning at present. */

    if (!dataCollection.getBibleStructure().standardBooksAreInOrder())
      Logger.warning("OT / NT books are not in order.")



    /**************************************************************************/
    /* Entire embedded chapters missing is definitely a problem -- I doubt
       either version of osis2mod will cope, and I have no processing to create
       dummy chapters (and even if I did, an entire empty chapter is probably
       iffy. */

    val missingEmbeddedChapters = dataCollection.getBibleStructure().getMissingEmbeddedChaptersForText()
    if (missingEmbeddedChapters.isNotEmpty())
      Logger.error("Locations where embedded chapters are missing: " + missingEmbeddedChapters.joinToString(", "){ Ref.rd(it).toString() })



    /**************************************************************************/
    /* Missing embedded verses would be a problem if things were left that way,
       because the osis2mod structure doesn't cope.  However, I have processing
       to fill in any holes -- either because reversification asks for it, or
       else as a backstop at the end of processing.  I think therefore that
       this need only be a backstop.

       Note that missing verses at the _ends_ of chapters are not a problem
       (and it may not always even be apparent if there _are_ such verses,
       because you don't always know what the last verse should be).  I don't
       bother detecting or reporting these, therefore. */

    val missingEmbeddedVerses = dataCollection.getBibleStructure().getMissingEmbeddedVersesForText()
    if (missingEmbeddedVerses.isNotEmpty())
      Logger.warning("Locations where embedded verses are missing: " + missingEmbeddedVerses.joinToString(", "){ Ref.rd(it).toString() })



    /**************************************************************************/
    /* Duplicates are a definite error. */

    val duplicateVerses = dataCollection.getBibleStructure().getDuplicateVersesForText()
    if (duplicateVerses.isNotEmpty())
      Logger.error("Locations where we have duplicate verses: " + duplicateVerses.joinToString(", "){ Ref.rd(it).toString() })
  }
}
