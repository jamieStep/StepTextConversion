package org.stepbible.textconverter.builders

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor
import java.util.*

/******************************************************************************/
/**
 * Base class for builders.
 *
 * In the main, builders are things which participate in the construction of
 * a repository module.
 *
 * There are also a few things which I have called 'SpecialBuilders', and which
 * do other things, like evaluating the goodness of fit between a text and the
 * various Crosswire schemes.  I suppose these aren't really building anything,
 * but it's convenient to include them here because they are rather intimately
 * tied up with the general processing.
 *
 * A Builder needs to implement ...
 *
 * -- banner(): A method which returns a string naming the builder, and which
 *    is used for progress reporting and debugging.  This may be empty if you
 *    don't think it worthwhile naming some minor piece of processing.
 *
 * -- commandLineOptions: A method which returns a possibly empty / null list
 *    of command line options which the builder supports / requires.
 *
 * -- doIt(): The method which actually does the processing.  Any builder which
 *    relies upon the output of a previous builder should begin by calling the
 *    'process' method of that builder.  After this it should use
 *    Dbg.reportProcess(banner()) to report that it is, itself running.  And
 *    after that it can do whatever it deems necessary.
 *
 *
 *
 * Setting aside the special builders, all of the builders with names starting
 * 'Builder_InitialOsisRepresentationFrom...' take one of the various forms of
 * input we can accept and converts it to an initial form of OSIS, which will
 * later be subject to further modification.
 *
 * Which of these we need to run depends upon what kinds of input are available,
 * and whether we have been told to use OSIS in preference to anything else.
 * This call is made by BuilderInitialOsisRepresentationOfInputs, which works
 * out what to do and then calls the appropriate one of the
 * Builder_InitialOsisRepresentationFrom... objects to do the work.
 *
 * Builder_InternalOsis converts the OSIS created by these objects to the form
 * needed to create a module (and while doing so also sets aside a file
 * containing what I have called elsewhere 'external OSIS', which is a form of
 * OSIS suitable to have tagging applied to in future, should we choose to do
 * so.
 *
 * @author ARA "Jamie" Jamieson
 */

interface Builder
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * Returns a banner to reflect progress.
   *
   * @return Banner
   */

  fun banner (): String


  /****************************************************************************/
  /**
   * Returns details of any command-line parameters this processor requires or
   * permits.
   *
   * @return List of command-line options supported by this processor.
   */

  fun commandLineOptions (): List<CommandLineProcessor.CommandLineOption>?


  /****************************************************************************/
  /**
  * Carries out the processing required to create the output implied by the
  * class name.
  */

  fun process ()
  {
    Builder_Root.pushProcessorName(banner()) // Save name of processor in case we need it in error messages.
    doIt()
    Builder_Root.popProcessorName()
  }



  /****************************************************************************/
  /**
  * Does the processing.  Not intended to be called other than by the 'process'
  * method.
  */

  fun doIt ()
}