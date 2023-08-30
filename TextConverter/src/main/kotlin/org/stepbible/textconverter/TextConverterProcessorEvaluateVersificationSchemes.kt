/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.BibleBookAndFileMapperRawUsx
import org.stepbible.textconverter.support.bibledetails.BibleStructureTextUnderConstruction
import org.stepbible.textconverter.support.bibledetails.BibleStructuresSupportedByOsis2modAll
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.shared.SharedData
import java.io.File

/******************************************************************************/
/**
* Evaluates the alternative versification schemes to see which is the best
* fit.  Note that on a run which invokes this processing, nothing else is
* done.
*
* This is organised as a descendant of [TextConverterProcessorBase], and
* indeed at one point it was used that way.  This is no longer the case,
* however, because now one of its primary functions is to evaluate the
* fit between the text and the osis2mod versification schemes in a run which
* does no more than that -- ie it doesn't really fit into the
* TextConverterProcessorBase processing philosophy.  I have nonetheless
* retained it as a descendant of that class in case I change my mind
* some time about the way things should be organised.
*
* @author ARA 'Jamie' Jamieson
*/

object TextConverterProcessorEvaluateVersificationSchemes: TextConverterProcessorBase()
{
  /****************************************************************************/
  override fun banner (): String
  {
    return "Evaluation of fit with versification schemes (low score is better; osis2mod lacking verses is bad):"
  }


  /****************************************************************************/
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)
  {
    commandLineProcessor.addCommandLineOption("evaluateSchemesOnly", 0, "Evaluate alternative osis2mod versification schemes only.", null, null, false)
  }


  /****************************************************************************/
  override fun pre (): Boolean
  {
    deleteFile(Pair(StandardFileLocations.getVersificationFilePath(), null))
    return true
  }


  /****************************************************************************/
  override fun runMe (): Boolean
  {
    return true
  }


  /****************************************************************************/
  override fun process (): Boolean
  {
    doIt()
    return ConfigData.getAsBoolean("stepEvaluateSchemesOnly", "no") // Prevent further processing from running.
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
    Dbg.reportProgress("  Evaluating versification schemes")
    BibleStructureTextUnderConstruction.populate(BibleBookAndFileMapperRawUsx, wantWordCount = false, reportIssues = true)
    val bookNumbersInRawUsx = BibleBookAndFileMapperRawUsx.getBookNumbersInOrder()
    BibleStructuresSupportedByOsis2modAll.getSchemes().forEach { evaluateScheme(it, bookNumbersInRawUsx) }
    val details = investigateResults()
    evaluateNeedForReversification()
    outputDetails(details)
  }


  /****************************************************************************/
  /* Determines whether we are to reversify or not.  This is driven in part by
     command-line parameters.  The user may give None / Basic / Basic? /
     Academic / Academic?.  Any of the options which lack question marks force
     the issue.

     Any option which has a question mark means that it is up to the processing
     here to make a call as to whether the text departs far enough from the
     NRSV(A) versification to warrant reversification.

     Note that the thresholds which determine this are essentially arbitrary, so
     you are at liberty to change them. */

  private fun evaluateNeedForReversification ()
  {
    var fromCommandLineOrConfig = (ConfigData.get("stepReversificationType") ?: "none").lowercase()

    if (!fromCommandLineOrConfig.contains("?")) return

    val doIt = SharedData.NrsvVersificationSchemeNumberOfExcessVerseEquivalentsInOsisScheme!!  > 10 || // Thresholds -- see above.
               SharedData.NrsvVersificationSchemeNumberOfMissingVerseEquivalentsInOsisScheme!! > 5
    if (doIt)
    {
      ConfigData.put("stepReversificationType", fromCommandLineOrConfig.replace("?", ""), true)
      ConfigData.put("stepReversificationBasis", "Converter decided to reversify because text departs too far from NRSV(A).", true)
    }
    else
    {
      ConfigData.put("stepReversificationType", "none", true)
      ConfigData.put("stepReversificationBasis", "Converter decided not to reversify because text is reasonably close to NRSV(A).", true)
    }
  }


  /****************************************************************************/
  /* Investigates the evaluations and records the best scheme and also details
     of how well NRSV(A) fits (useful where it has been left to the processing
     to decide whether to reversify or not). */

  private fun investigateResults (): List<Evaluation>
  {
    val details = m_Evaluations.sortedBy { it.score }

    SharedData.BestVersificationScheme = details[0].scheme
    SharedData.BestVersificationSchemeScore= details[0].score

    val ix = details.indexOfFirst { it.scheme.matches("(?i)nrsv(a)?".toRegex()) }
    if (null != ix)
    {
      SharedData.NrsvVersificationScheme = details[ix].scheme
      SharedData.BestVersificationSchemeScore = details[ix].score
      SharedData.NrsvVersificationSchemeNumberOfExcessVerseEquivalentsInOsisScheme = details[ix].versesInExcessInOsis2modScheme
      SharedData.NrsvVersificationSchemeNumberOfMissingVerseEquivalentsInOsisScheme = details[ix].versesMissingInOsis2modScheme
    }

    return details
  }


  /****************************************************************************/
  private fun evaluateScheme (scheme: String, bookNumbersInTextUnderConstruction: List<Int>)
  {
    /**************************************************************************/
    Dbg.reportProgress("  Evaluating $scheme")
    val bibleStructureOther = BibleStructuresSupportedByOsis2modAll.getStructureFor(scheme)



    /**************************************************************************/
    if (BibleStructureTextUnderConstruction.hasDc() && !bibleStructureOther.hasDc())
    {
      m_Evaluations.add(Evaluation(scheme,999_999, 0, 0, "rejected because it lacks DC"))
      return
    }



    /**************************************************************************/
    /* No point in looking at the version with DC on KJVA and NRSVA if the
       text being processed doesn't have DC -- the versions without will be
       fine. */

    if (!BibleStructureTextUnderConstruction.hasDc() && ("kjva" == scheme || "nrsva" == scheme))
      return



     /**************************************************************************/
     var versesMissingInOsis2modScheme = 0
     var versesInExcessInOsis2modScheme = 0

     fun evaluate (bookNumber: Int)
     {
       val comparisonDetails = BibleStructureTextUnderConstruction.compareWithGivenScheme(bookNumber, bibleStructureOther)
       versesMissingInOsis2modScheme += comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme.size
       versesInExcessInOsis2modScheme += comparisonDetails.versesInTargetSchemeButNotInTextUnderConstruction.size
     }

     bookNumbersInTextUnderConstruction.forEach { evaluate(it) }



     /**************************************************************************/
     val score = versesMissingInOsis2modScheme * 1000 + versesInExcessInOsis2modScheme
     m_Evaluations.add(Evaluation(scheme, score, versesMissingInOsis2modScheme, versesInExcessInOsis2modScheme, null))
 }


  /****************************************************************************/
  private fun outputDetails (details: List<Evaluation>)
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
                    
                    """.trimIndent()

   File(StandardFileLocations.getVersificationFilePath()).printWriter().use {
      it.print(header)
      details.forEach { println(it.toString()) }
    }
  }


  /****************************************************************************/
  private data class Evaluation (val scheme: String, val score: Int, val versesMissingInOsis2modScheme: Int, val versesInExcessInOsis2modScheme: Int, val text: String?)
  {
    override fun toString (): String
    {
      return String.format("Scheme: %12s   Score: %10d   Based upon    %6d verses which osis2mod lacks   AND   %6d verses which osis2mod has in excess%s.",
                          scheme, score, versesMissingInOsis2modScheme, versesInExcessInOsis2modScheme, if (null == text) "" else "   " + text)
    }

  }



  /****************************************************************************/
  private val m_Evaluations: MutableList<Evaluation> = ArrayList()
}