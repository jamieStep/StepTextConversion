package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.bibledetails.VersificationSchemesSupportedByOsis2mod
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.stepexception.StepBreakOutOfProcessing
import org.stepbible.textconverter.utils.*
import java.io.File


/******************************************************************************/
/**
  * Checks to see if all we are doing is to evaluate schemes.
  *
  * @author ARA "Jamie" Jamieson
  */

object SpecialBuilder_EvaluateSchemesOnly: SpecialBuilder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Evaluating fit with versification schemes"
  override fun commandLineOptions () = listOf(
    CommandLineProcessor.CommandLineOption("evaluateSchemesOnly", 0, "Evaluate alternative osis2mod versification schemes only.", null, null, false),
  )





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun doIt ()
  {
    /**************************************************************************/
    if (!ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "no"))
      return



    /**************************************************************************/
    StepFileUtils.deleteFile(FileLocations.getVersificationFilePath())
    Dbg.reportProgress(banner())
    Dbg.resetBooksToBeProcessed() // Force all books to be included.
    Builder_InitialOsisRepresentationOfInputs.process()
    evaluate()
  }


  /****************************************************************************/
  /* We will get here only under one of two circumstances.  The user may have
     specified evaluateSchemesOnly on the command line (ie we are being called
     to take the versification structure actually present in the text being
     processed and to report how well it fits with each of the schemes
     supported by osis2mod); or we may be being called because the user has
     specified basic? or academic? as reversification options, which leave it
     to the converter to decide whether the text fits NRSV(A) well enough not
     to require reversification.

     In the former case, we merely write a report file.  In the latter, we make
     the necessary call and then update various configuration options to reflect
     the decision, but we don't create the report file. */

  private fun evaluate ()
  {
    val dataCollection = X_DataCollection(Osis_FileProtocol)
    dataCollection.loadFromText(Phase1TextOutput)
    val bibleStructureToCompareWith = dataCollection.getBibleStructure()

    m_Evaluations.clear() // Just in case we've already evaluated a scheme, perhaps to see if the text needs reversifying.  Avoids duplicating the output.
    VersificationSchemesSupportedByOsis2mod.getSchemes().forEach { evaluateScheme(it, bibleStructureToCompareWith) }
    val details = m_Evaluations.sortedBy { it.scoreForSorting }

    var additionalInformation = ""
    if (!bibleStructureToCompareWith.otBooksAreInOrder() || !bibleStructureToCompareWith.ntBooksAreInOrder()) additionalInformation  = "*** Text contains out-of-order books. ***\n"
    if (bibleStructureToCompareWith.hasSubverses()) additionalInformation += "*** Text contains subverses. ***\n"
    if (!bibleStructureToCompareWith.versesAreInOrder()) additionalInformation += "*** Text contains out-of-order verses. ***"
    if (additionalInformation.endsWith("\n")) additionalInformation = additionalInformation.substring(0, additionalInformation.length - 1)

    outputDetails(details, additionalInformation)

    throw StepBreakOutOfProcessing("")
  }


  /****************************************************************************/
  private fun evaluateScheme (scheme: String, bibleStructureToCompareWith: BibleStructure): Evaluation
  {
    /**************************************************************************/
    //Dbg.dCont(scheme, "nrsv")
    //Dbg.dCont(scheme, "kjv")



    /**************************************************************************/
    Dbg.reportProgress("Evaluating $scheme")
    val bibleStructureForScheme = BibleStructure.makeOsis2modSchemeInstance(scheme)
    var additionalText: String? = null



    /**************************************************************************/
    val bibleStructureToCompareWithHasDc = bibleStructureToCompareWith.hasAnyBooksDc()
    val osis2modSchemeHasDc = bibleStructureForScheme.hasAnyBooksDc()
    if (bibleStructureToCompareWithHasDc && !osis2modSchemeHasDc)
    {
      val x = Evaluation(scheme, Int.MAX_VALUE, 0, 0, 0, 0, 0, text = "Rejected because it lacks DC.")
      m_Evaluations.add(x)
      return x
    }



    /**************************************************************************/
    if (!bibleStructureToCompareWithHasDc && osis2modSchemeHasDc)
      additionalText = "(Scheme has DC where text does not.)"



    /**************************************************************************/
    var booksMissingInOsis2modScheme = 0
    var booksInExcessInOsis2modScheme = 0
    var versesMissingInOsis2modScheme = 0
    var versesInExcessInOsis2modScheme = 0
    var versesOutOfOrder = 0

    fun evaluate (bookNumber: Int)
    {
      if (!bibleStructureToCompareWith.bookExists(bookNumber) && !bibleStructureForScheme.bookExists(bookNumber))
        return

      else if (!bibleStructureToCompareWith.bookExists(bookNumber))
        ++booksInExcessInOsis2modScheme

      else if (!bibleStructureToCompareWith.bookExists(bookNumber))
        ++booksMissingInOsis2modScheme

      else
      {
        val comparisonDetails = BibleStructure.compareWithGivenScheme(bookNumber, bibleStructureToCompareWith, bibleStructureForScheme)
        versesMissingInOsis2modScheme += comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme.size
        versesInExcessInOsis2modScheme += comparisonDetails.versesInTargetSchemeButNotInTextUnderConstruction.size
        versesOutOfOrder += comparisonDetails.versesInTextUnderConstructionOutOfOrder.size
      }
    }

    bibleStructureToCompareWith.getAllBookNumbers().forEach { evaluate(it) }



    /**************************************************************************/
    val score = 1_000_000_000 * versesOutOfOrder.coerceAtMost(1) + booksMissingInOsis2modScheme * 1_000_000 + versesMissingInOsis2modScheme * 1000 + versesInExcessInOsis2modScheme
    val res = Evaluation(scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, versesOutOfOrder, additionalText)
    m_Evaluations.add(res)
    return res
  }


  /****************************************************************************/
  private fun outputDetails (details: List<Evaluation>, additionalInformation: String)
  {
    /**************************************************************************/
     val header = """
                    ################################################################################
                    #
                    # When running osis2mod, you have to specify the versification scheme to which
                    # your text conforms (or more or less conforms).
                    #
                    # If you are applying reversification, the processing always forces the scheme
                    # to be NRSV(A), and since you have no choice in the matter, this present file
                    # is of no interest.
                    #
                    # If you are _not_ applying reversification, however, you need to choose one of
                    # the schemes supported by osis2mod; and you need to choose one which is a good
                    # fit (to the extent that this is possible), because if osis2mod is presented
                    # with a text which does not fit the scheme it has been asked to use, it may
                    # amalgamate verses or even (I think) drop them, which is clearly an undesirable
                    # situation.
                    #
                    # This file contains a ranking of the various osis2mod versification schemes.
                    # Given that it is comparatively unusual (based upon our experience with
                    # reversification) to come across an exact fit, you will almost certainly
                    # need to choose between schemes which do not quite fit.  Rather than simply
                    # give you a proposal, I include below the internally-generated scores for
                    # the various schemes.
                    #
                    # In general, it is a Bad Thing if osis2mod does not have the books / chapters /
                    # verses which appear in your text -- much better to opt for a scheme where
                    # osis2mod contains elements which are _not_ needed than one where it lacks
                    # elements which _are_ needed.
                    #
                    # Hopefully the scoring below will help with the choice.  The options are listed
                    # best fit first, based upon my (fairly arbitrary) scoring, which is as
                    # follows :-
                    #
                    #   Larger scores are worse.
                    #
                    #   0 implies a perfect fit.
                    #
                    #   A verse which osis2mod expects, but which is not available in the raw text,
                    #   scores 1.
                    #
                    #   A verse which osis2mod does _not_ expect, but which is present in the raw
                    #   text, scores 1000.  This reflects my comment above, that osis2mod may
                    #   amalgamate verses which it is not expecting -- something which is probably
                    #   undesirable.
                    #
                    #   Schemes which are completely infeasible are given a score of 999,999.
                    #
                    #   I never evaluate both KJV _and_ KJVA or both NRSV _and_ NRSVA -- in each
                    #   case I choose one or other from the pair, according to whether the text
                    #   being handled does or does not have DC books.
                    #
                    ################################################################################
                    
                    ${ConfigData["stepModuleName"]!!}
                    
                    """.trimIndent()

   File(FileLocations.getVersificationFilePath()).printWriter().use { writer ->
      writer.print(header + "\n")
      details.forEach { writer.print(it.toString()); writer.print("\n"); println(it.toString()) }
      if (additionalInformation.isNotEmpty())
      {
        writer.print("\n"); println("")
        writer.print(additionalInformation + "\n"); println(additionalInformation)
      }
    }
  }


  /****************************************************************************/
  enum class VersificationDeviationType { EXACT_MATCH, BAD, OK }


  /****************************************************************************/
  data class Evaluation (val scheme: String,
                         val score: Int,
                         val booksMissingInOsis2modScheme: Int,
                         val versesMissingInOsis2modScheme: Int,
                         val booksInExcessInOsis2modScheme: Int,
                         val versesInExcessInOsis2modScheme: Int,
                         val versesOutOfOrder: Int,
                         val text: String?)
  {
    fun exactMatch (): Boolean = 0 == booksMissingInOsis2modScheme && 0 == versesMissingInOsis2modScheme && 0 == booksInExcessInOsis2modScheme && 0 == versesInExcessInOsis2modScheme

    fun getDeviationType (): VersificationDeviationType
    {
      return if (0 == score)
        VersificationDeviationType.EXACT_MATCH
      else if (booksMissingInOsis2modScheme > 0 || versesMissingInOsis2modScheme > 0 || versesOutOfOrder > 0)
        VersificationDeviationType.BAD
      else
        VersificationDeviationType.OK
    }

    override fun toString (): String
    {
      return if (Int.MAX_VALUE == score)
        String.format("Scheme: %12s   %s", scheme, text!!)
      else
        String.format("Scheme: %12s   Score: %12d   Based upon    %3d books and %6d verses which osis2mod lacks   AND   %3d books and %6d verses which osis2mod has in excess.%s",
                      scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, if (null == text) "" else "  $text")
    }

    fun toStringShort (): String?
    {
      return if (0 == booksMissingInOsis2modScheme && 0 == versesMissingInOsis2modScheme && 0 == booksInExcessInOsis2modScheme && 0 == versesInExcessInOsis2modScheme)
        null
      else
      {
        val xbooksMissingInOsis2modScheme = if (0 == booksMissingInOsis2modScheme) "" else "$booksMissingInOsis2modScheme books"
        val xversesMissingInOsis2modScheme = if (0 == versesMissingInOsis2modScheme) "" else "$versesMissingInOsis2modScheme verses"
        var xand = if (0 == booksMissingInOsis2modScheme && 0 == versesMissingInOsis2modScheme) "" else " and "
        val textExcess = if (0 == booksMissingInOsis2modScheme && 0 == versesMissingInOsis2modScheme) "" else "BAD divergences: Text has $xbooksMissingInOsis2modScheme$xand$xversesMissingInOsis2modScheme which the scheme lacks."

        val xbooksInExcessInOsis2modScheme = if (0 == booksInExcessInOsis2modScheme) "" else "$booksInExcessInOsis2modScheme books"
        val xversesInExcessInOsis2modScheme = if (0 == versesInExcessInOsis2modScheme) "" else "$versesInExcessInOsis2modScheme verses"
        xand = if (0 == booksInExcessInOsis2modScheme && 0 == versesInExcessInOsis2modScheme) "" else " and "
        val textLacks = if (0 == booksInExcessInOsis2modScheme && 0 == versesInExcessInOsis2modScheme) "" else "OK-ish divergences: Text lacks $xbooksInExcessInOsis2modScheme$xand$xversesInExcessInOsis2modScheme which the scheme expects."

        val sep = if (textExcess.isEmpty() || textLacks.isEmpty()) "" else "  "

        return textExcess + sep + textLacks
      }
    }

    // Want to favour NRSV(A) over other schemes which may score the same.
    val scoreForSorting: Double = if ("nrsv" == scheme) score.toDouble() - 0.2 else if ("nrsva" == scheme) score.toDouble() - 0.1 else score.toDouble()
  }


  /****************************************************************************/
  private val m_Evaluations: MutableList<Evaluation> = ArrayList()

}
