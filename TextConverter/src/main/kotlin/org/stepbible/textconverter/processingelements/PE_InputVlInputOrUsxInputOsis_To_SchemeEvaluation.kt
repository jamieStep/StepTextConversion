/******************************************************************************/
package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.FileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.ref.RefKey
import org.stepbible.textconverter.utils.*
import java.io.File

/******************************************************************************/
/**
* Evaluates the alternative versification schemes to see which is the best
* fit.  Note that on a run which invokes this processing, nothing else is
* done.
*
* This is organised as a descendant of [PE], and
* indeed at one point it was used that way.  This is no longer the case,
* however, because now one of its primary functions is to evaluate the
* fit between the text and the osis2mod versification schemes in a run which
* does no more than that -- ie it doesn't really fit into the
* TextConverterProcessorBase processing philosophy.  I have nonetheless
* retained it as a descendant of that class in case I change my mind
* some time about the way things should be organised.
*
* Note that I make the assumption that any pre-processing which will be applied
* to the text during an actual run of the converter will not change the
* versification structure of the text, and that therefore it is ok to evaluate
* the versification schemes against the raw USX.
*
* Input comes from OsisInternal.  Output goes to the screen and to a log file.
*
* @author ARA 'Jamie' Jamieson
*/

object PE_InputVlInputOrUsxInputOsis_To_SchemeEvaluation: PE
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  override fun banner () = "Evaluating schemes"
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) {}
  override fun pre () { StepFileUtils.deleteFile(FileLocations.getOsis2modVersificationDetailsFilePath()) }
  override fun process () = doIt()



  /****************************************************************************/
  class DetailedEvaluation
  {
    lateinit var DCPresence: String
    var booksMissingInOsis2modScheme: MutableList<Int> = mutableListOf()
    var booksInExcessInOsis2modScheme: MutableList<Int> = mutableListOf()
    var versesMissingInOsis2modScheme: MutableList<RefKey> = mutableListOf()
    var versesInExcessInOsis2modScheme: MutableList<RefKey> = mutableListOf()
  }


  /****************************************************************************/
  /**
  * A late addition.  Compares a given text with a specified scheme and returns
  * a detailed analysis.  This was introduced to make it possible to report any
  * issues in the TextFeatures details.  I really ought to make the time to
  * integrate it properly -- the outline analysis reported in Evaluation
  * could make use of this, for instance.
  *
  * @param scheme osis2mod scheme to be analysed.
  * @param bibleStructureToCompareWith Scheme against which we are comparing.
  * @return Detailed evaluation.
  */

  fun evaluateSingleSchemeDetailed (scheme: String, bibleStructureToCompareWith: BibleStructure): DetailedEvaluation
  {
    /**************************************************************************/
    Dbg.reportProgress("Evaluating $scheme")
    val res = DetailedEvaluation()
    val bibleStructureOsis2mod = BibleStructure.makeOsis2modSchemeInstance(scheme)



    /**************************************************************************/
    val bibleStructureToCompareWithHasDc = bibleStructureToCompareWith.hasAnyBooksDc()
    val schemeHasDc = bibleStructureOsis2mod.hasAnyBooksDc()



    /**************************************************************************/
    res.DCPresence =
      if (!bibleStructureToCompareWithHasDc && schemeHasDc)
        "MAJOR ISSUE: Input text contains DC, but selected scheme does not"
      else if (bibleStructureToCompareWithHasDc && !schemeHasDc)
        "MODERATE ISSUE: Selected scheme contains DC, but input text does not"
      else if (bibleStructureToCompareWithHasDc)
        "DC present"
      else
        "DC absent"



    /**************************************************************************/
    fun evaluate (bookNumber: Int)
     {
       if (!bibleStructureToCompareWith.bookExists(bookNumber) && !bibleStructureOsis2mod.bookExists(bookNumber))
         return

       else if (!bibleStructureToCompareWith.bookExists(bookNumber))
         res.booksInExcessInOsis2modScheme.add(bookNumber)

       else if (!bibleStructureOsis2mod.bookExists(bookNumber))
         res.booksMissingInOsis2modScheme.add(bookNumber)

       else
       {
         val comparisonDetails = BibleStructure.compareWithGivenScheme(bookNumber, bibleStructureToCompareWith, bibleStructureOsis2mod)
         res.versesMissingInOsis2modScheme.addAll(comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme)
         res.versesInExcessInOsis2modScheme.addAll(comparisonDetails.versesInTargetSchemeButNotInTextUnderConstruction)
       }
     }

     bibleStructureToCompareWith.getAllBookNumbersOt().forEach { evaluate(it) }
     bibleStructureToCompareWith.getAllBookNumbersNt().forEach { evaluate(it) }
     bibleStructureToCompareWith.getAllBookNumbersDc().forEach { evaluate(it) }



     /**************************************************************************/
     return res
  }


  /****************************************************************************/
  /**
  * Returns the evaluation details for a single scheme.
  *
  * @param schemeName Must be in canonical form.
  * @param bibleStructureToCompareWith Scheme against which we are comparing.
  * @return Result of evaluating against the given scheme.
  */

  fun evaluateSingleScheme (schemeName: String, bibleStructureToCompareWith: BibleStructure): Evaluation?
  {
    //???val bookNumbersInRawUsx = bibleStructureToCompareWith.getAllBookNumbers().toList()
    return evaluateScheme(schemeName, bibleStructureToCompareWith)
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

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

  private fun doIt ()
  {
    val bibleStructureToCompareWith = determineInput()
    m_Evaluations.clear() // Just in case we've already evaluated a scheme, perhaps to see if the text needs reversifying.  Avoids duplicating the output.
    VersificationSchemesSupportedByOsis2mod.getSchemes().forEach { evaluateScheme(it, bibleStructureToCompareWith) }
    val details = m_Evaluations.sortedBy { it.scoreForSorting }

    var additionalInformation: String? = null
    if (!bibleStructureToCompareWith.otBooksAreInOrder() || !bibleStructureToCompareWith.ntBooksAreInOrder()) additionalInformation  = "*** Text contains out-of-order books. ***\n"
    if (!bibleStructureToCompareWith.versesAreInOrder()) additionalInformation += "*** Text contains out-of-order verses. ***"
    if (null != additionalInformation && additionalInformation.endsWith("\n")) additionalInformation = additionalInformation.substring(0, additionalInformation.length - 1)
    outputDetails(details, additionalInformation)
  }


  /****************************************************************************/
  private fun determineInput (): BibleStructure
  {
    /**************************************************************************/
    when (ConfigData["stepOriginData"]!!)
    {
      "osis" ->
      {
        PE_Phase1_FromInputOsis.pre()
        PE_Phase1_FromInputOsis.process()
      }

      "usx" ->
      {
        PE_Phase1_FromInputUsx.pre()
        PE_Phase1_FromInputUsx.process()
      }

      "vl" ->
      {
        PE_Phase1_FromInputVl.pre()
        PE_Phase1_FromInputVl.process()
      }
    }



    /**************************************************************************/
    OsisPhase1OutputDataCollection.loadFromText(OsisPhase1OutputDataCollection.getText(), false)
    return OsisPhase1OutputDataCollection.BibleStructure
  }


  /****************************************************************************/
  private fun evaluateScheme (scheme: String, bibleStructureToCompareWith: BibleStructure): Evaluation?
  {
    /**************************************************************************/
    //Dbg.dCont(scheme, "nrsv")
    //Dbg.dCont(scheme, "kjv")



    /**************************************************************************/
    /* "tbd" is used where we are doing runtime reversification and have not
       yet decided on a scheme name.  Even if we had an actual scheme name
       available here, there would be no point in evaluating against it,
       because the scheme would be a bespoke one, and we're guaranteed to fit
       that. */

    if ("tbd" == scheme)
      return null



    /**************************************************************************/
    Dbg.reportProgress("Evaluating $scheme")
    val bibleStructureForScheme = BibleStructure.makeOsis2modSchemeInstance(scheme)
    var additionalText: String? = null



    /**************************************************************************/
    val bibleStructureToCompareWithHasDc = bibleStructureToCompareWith.hasAnyBooksDc()
    val osis2modSchemeHasDc = bibleStructureForScheme.hasAnyBooksDc()
    if (bibleStructureToCompareWithHasDc && !osis2modSchemeHasDc)
    {
      val x = Evaluation(scheme, Int.MAX_VALUE, 0, 0, 0, 0, text = "Rejected because it lacks DC.")
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
      }
    }

    bibleStructureToCompareWith.getAllBookNumbers().forEach { evaluate(it) }



    /**************************************************************************/
    val score = booksMissingInOsis2modScheme * 1_000_000 + versesMissingInOsis2modScheme * 1000 + versesInExcessInOsis2modScheme
    val res = Evaluation(scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, additionalText)
    m_Evaluations.add(res)
    return res
  }


  /****************************************************************************/
  private fun outputDetails (details: List<Evaluation>, additionalInformation: String?)
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
                    
                    ${ConfigData["stepModuleNameBase"]!!}
                    
                    """.trimIndent()

   File(FileLocations.getVersificationFilePath()).printWriter().use { writer ->
      writer.println(header)
      details.forEach { writer.println(it.toString()); println(it.toString()) }
      if (null != additionalInformation)
      {
        writer.println(""); println("")
        writer.println(additionalInformation); println(additionalInformation)
      }
    }
  }


  /****************************************************************************/
  data class Evaluation (val scheme: String,
                         val score: Int,
                         val booksMissingInOsis2modScheme: Int,
                         val versesMissingInOsis2modScheme: Int,
                         val booksInExcessInOsis2modScheme: Int,
                         val versesInExcessInOsis2modScheme: Int,
                         val text: String?)
  {
    override fun toString (): String
    {
      return if (Int.MAX_VALUE == score)
        String.format("Scheme: %12s   %s", scheme, text!!)
      else
        String.format("Scheme: %12s   Score: %12d   Based upon    %3d books and %6d verses which osis2mod lacks   AND   %3d books and %6d verses which osis2mod has in excess.%s",
                      scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, if (null == text) "" else "  $text")
    }

    // Want to favour NRSV(A) over other schemes which may score the same.
    val scoreForSorting: Double = if ("nrsv" == scheme) score.toDouble() - 0.2 else if ("nrsva" == scheme) score.toDouble() - 0.1 else score.toDouble()
  }


  /****************************************************************************/
  private val m_Evaluations: MutableList<Evaluation> = ArrayList()
}