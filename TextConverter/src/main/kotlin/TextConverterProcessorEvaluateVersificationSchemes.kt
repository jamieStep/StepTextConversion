/******************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.bibledetails.*
import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.configdata.StandardFileLocations
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.StepFileUtils
import org.stepbible.textconverter.support.shared.SharedData
import java.io.File

/******************************************************************************/
/**
* Evaluates the alternative versification schemes to see which is the best
* fit.  Note that on a run which invokes this processing, nothing else is
* done.
*
* This is organised as a descendant of [TextConverterProcessor], and
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
* @author ARA 'Jamie' Jamieson
*/

object TextConverterProcessorEvaluateVersificationSchemes: TextConverterProcessor
{
  /****************************************************************************/
  override fun banner () = "Evaluating fit with versification schemes"
  override fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor) = commandLineProcessor.addCommandLineOption("evaluateSchemesOnly", 0, "Evaluate alternative osis2mod versification schemes only.", null, null, false)
  override fun prepare () = StepFileUtils.deleteFile(StandardFileLocations.getVersificationFilePath())
  override fun process () = doIt()



  /****************************************************************************/
  /**
  * Returns the evaluation details for a single scheme.
  *
  * @param schemeName Must be in canonical form.
  * @return Result of evaluating against the given scheme.
  */

  fun evaluateSingleScheme (schemeName: String): Evaluation?
  {
    val bookNumbersInRawUsx = BibleBookAndFileMapperStandardUsx.getBookNumbersInOrder()
    return evaluateScheme(schemeName, bookNumbersInRawUsx)
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
    m_Evaluations.clear() // Just in case we've already evaluated a scheme, perhaps to see if the text needs reversifying.  Avoids duplicating the output.
    BibleStructure.UsxUnderConstructionInstance().populateFromBookAndFileMapper(BibleBookAndFileMapperStandardUsx, "raw", wantWordCount = false)
    val bookNumbersInRawUsx = BibleBookAndFileMapperStandardUsx.getBookNumbersInOrder()
    BibleStructuresSupportedByOsis2mod.getSchemes().forEach { evaluateScheme(it, bookNumbersInRawUsx) }
    val details = investigateResults()
    outputDetails(details)
  }


  /****************************************************************************/
  /* Investigates the evaluations and records the best scheme and also details
     of how well NRSV(A) fits (useful where it has been left to the processing
     to decide whether to reversify or not). */

  private fun investigateResults (): List<Evaluation>
  {
    val details = m_Evaluations.sortedBy { it.scoreForSorting }

    SharedData.BestVersificationScheme = details[0].scheme
    SharedData.BestVersificationSchemeScore= details[0].score

    val ix = details.indexOfFirst { it.scheme.matches("(?i)nrsv(a)?".toRegex()) }
    SharedData.NrsvVersificationScheme = details[ix].scheme
    SharedData.BestVersificationSchemeScore = details[ix].score
    SharedData.NrsvVersificationSchemeNumberOfExcessVerseEquivalentsInOsisScheme = details[ix].versesInExcessInOsis2modScheme
    SharedData.NrsvVersificationSchemeNumberOfMissingVerseEquivalentsInOsisScheme = details[ix].versesMissingInOsis2modScheme

    return details
  }


  /****************************************************************************/
  private fun evaluateScheme (scheme: String, bookNumbersInTextUnderConstruction: List<Int>): Evaluation?
  {
    /**************************************************************************/
    //Dbg.dCont(scheme, "nrsv")
    //Dbg.dCont(scheme, "kjv")



    /**************************************************************************/
    Dbg.reportProgress("  Evaluating $scheme")
    val bibleStructureOther = BibleStructure.Osis2modSchemeInstance(scheme, false)



    /**************************************************************************/
    val textUnderConstructionHasDc = BibleStructure.UsxUnderConstructionInstance().hasAnyBooksDc()
    val otherHasDc = bibleStructureOther.hasAnyBooksDc()
    if (textUnderConstructionHasDc && !otherHasDc)
    {
      //m_Evaluations.add(Evaluation(scheme,999_999, 0, 0, "rejected because it lacks DC"))
      return null
    }



    /**************************************************************************/
    if (!textUnderConstructionHasDc && otherHasDc)
      return null



     /**************************************************************************/
     var booksMissingInOsis2modScheme = 0
     var booksInExcessInOsis2modScheme = 0
     var versesMissingInOsis2modScheme = 0
     var versesInExcessInOsis2modScheme = 0

     fun evaluate (bookNumber: Int)
     {
       if (!BibleStructure.UsxUnderConstructionInstance().bookExists(bookNumber) && !bibleStructureOther.bookExists(bookNumber))
         return

       else if (!BibleStructure.UsxUnderConstructionInstance().bookExists(bookNumber))
         ++booksInExcessInOsis2modScheme

       else if (!bibleStructureOther.bookExists(bookNumber))
         ++booksMissingInOsis2modScheme

       else
       {
         val comparisonDetails = BibleStructure.compareWithGivenScheme(bookNumber, BibleStructure.UsxUnderConstructionInstance(), bibleStructureOther)
         versesMissingInOsis2modScheme += comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme.size
         versesInExcessInOsis2modScheme += comparisonDetails.versesInTargetSchemeButNotInTextUnderConstruction.size
         // if (comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme.isNotEmpty()) Dbg.d(comparisonDetails.versesInTextUnderConstructionButNotInTargetScheme.joinToString(prefix = "Bad: ", separator = ", "){ Ref.rd(it).toString() })
         //Dbg.d(comparisonDetails.versesInTargetSchemeButNotInTextUnderConstruction.joinToString(prefix = "Ok: ", separator = ", "){ Ref.rd(it).toString() })
       }
     }

     bookNumbersInTextUnderConstruction.forEach { evaluate(it) }



     /**************************************************************************/
     val score = booksMissingInOsis2modScheme * 1_000_000 + versesMissingInOsis2modScheme * 1000 + versesInExcessInOsis2modScheme
     val res = Evaluation(scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, null)
     m_Evaluations.add(res)
     return res
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
                    
                    ${ConfigData["stepModuleName"]!!}
                    
                    """.trimIndent()

   File(StandardFileLocations.getVersificationFilePath()).printWriter().use { writer ->
      writer.println(header)
      details.forEach { writer.println(it.toString()); println(it.toString()) }
    }
  }


  /****************************************************************************/
  data class Evaluation (val scheme: String,
                         val score: Int,
                         val booksMissingInOsis2modScheme: Int,
                         val versesMissingInOsis2modScheme: Int,
                         val booksInExcessInOsis2modScheme: Int,
                         val versesInExcessInOsis2modScheme: Int, val text: String?)
  {
    override fun toString (): String
    {
      return String.format("Scheme: %12s   Score: %12d   Based upon    %3d books and %6d verses which osis2mod lacks   AND   %3d books and %6d verses which osis2mod has in excess%s.",
                          scheme, score, booksMissingInOsis2modScheme, versesMissingInOsis2modScheme, booksInExcessInOsis2modScheme, versesInExcessInOsis2modScheme, if (null == text) "" else "   $text")
    }

    // Want to favour NRSV(A) over other schemes which may score the same.
    val scoreForSorting: Double = if ("nrsv" == scheme) score.toDouble() - 0.2 else if ("nrsva" == scheme) score.toDouble() - 0.1 else score.toDouble()
  }



  /****************************************************************************/
  private val m_Evaluations: MutableList<Evaluation> = ArrayList()
}