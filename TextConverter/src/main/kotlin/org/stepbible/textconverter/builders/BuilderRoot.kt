package org.stepbible.textconverter.builders

import org.stepbible.textconverter.nonapplicationspecificutils.commandlineprocessor.CommandLineProcessor
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg

/******************************************************************************/
/**
 * Base class for builders.
 *
 * In the main, builders are things which participate in the construction of
 * a repository module.
 *
 * Anything with an underscore in its name inherits indirectly from this class.
 * Things whose names don't contain an underscore are shared utilities.
 *
 * Builders are things involved in the processing chain which converts inputs
 * into modules and repository packages.  These inherit from [SpecialBuilder]
 * which itself inherits from the present class.
 *
 * SpecialBuilders aren't really builders at all, but have enough
 * characteristics in common with builders to make it convenient to include
 * them here.  They do things like evaluating the goodness of fit between a text
 * and the various Crosswire versification schemes.  These inherit from
 * [Builder] which itself inherits from the present class.
 *
 * A Builder or SpecialBuilder needs to implement ...
 *
 * - **[banner]**: A method which returns a string naming the builder, and which
 *   is used for progress reporting and debugging.  This may be empty if you
 *   don't think it worthwhile naming some minor piece of processing.
 *
 * - **[commandLineOptions]**: A method which returns a possibly empty / null
 *   list of command line options which the builder supports / requires.
 *
 * - **[doIt]**: The method which actually does the processing.  The *doIt* for
 *   any builder which relies upon the output of a previous builder should begin
 *   by calling the 'process' method of that builder (defined in this present
 *   class).  After this it should use *Dbg.reportProcess(banner())* to report
 *   that it is, itself running.  And after that it can do whatever it deems
 *   necessary.
 *
 *
 * [Builder_Master] controls the overall process, and also adds various command-
 * line parameters which are commonly used but which otherwise have no obvious
 * home.
 *
 * All of the builders with names starting 'Builder_InitialOsisRepresentationFrom...'
 * take one of the various forms of input we can accept and convert it to an
 * initial form of OSIS, which will later be subject to further modification.
 * That is the entire remit of Builder_InitialOsisRepresentationFromOsis.  The
 * others, in addition to creating an initial OSIS representation, also store
 * that representation in the InputOsis folder, under the name DONT_USE_ME.xml.
 * The file is renamed to something more meaningful later in the processing when
 * we know things have worked.
 *
 * Which of these we need to run depends upon what kinds of input are available,
 * and whether we have been told to use OSIS in preference to anything else.
 * This call is made by [Builder_InitialOsisRepresentationOfInputs], which works
 * out what to do and then calls the appropriate one of the
 * Builder_InitialOsisRepresentationFrom... objects to do the work.
 *
 * The remit of all the Builder_InitialOsisRepresentationFrom... is similar.
 * The all need to convert there inputs into as faithful as possible an OSIS
 * representation (by which I mean that the OSIS needs to be a direct
 * representation of the input, with as little done to it as possible).  This
 * they should store
 *
 * The one exception is Builder_InitialOsisRepresentationFromOsis.  This, as the
 * name suggests, takes OSIS as its input -- either because that is the starting
 * data supplied to us by third parties, or because we've taken the OSIS created
 * in a previous run and applied changes to it (perhaps tagging), and now want
 * to use that OSIS itself as input.  It still needs to create a DOM with
 * minimal changes to the input, but it does not store any data over the top of
 * the input file.
 *
 * [Builder_InternalOsis] converts the OSIS created by these objects to the form
 * needed to create a module (and while doing so also sets aside a file
 * containing what I have called elsewhere 'external OSIS', which is a form of
 * OSIS suitable for the future application of things like tagging.
 *
 * [Builder_Module] converts the output of Builder_InternalOsis to a module.
 *
 * [Builder_RepositoryPackage] creates the repository package.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class BuilderRoot
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
   * Returns a string describing what the class is doing.  This is used for
   * progress reports and error messages.
   *
   * @return Banner
   */

  abstract fun banner (): String


  /****************************************************************************/
  /**
   * Returns details of any command-line parameters this processor requires or
   * accepts.
   *
   * @return List of command-line options supported by this processor.
   */

  abstract fun commandLineOptions (): List<CommandLineProcessor.CommandLineOption>?


  /****************************************************************************/
  /**
  * Carries out the processing required to create the output implied by the
  * derived class name.
  */

  fun process ()
  {
    try {
      Dbg.pushActiveProcessingIds(banner()) // Save name of processor for use in error and progress messages.
      doIt()
    }
    finally {
      Dbg.popActiveProcessingIds()
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Does the processing.  Not intended to be called other than by the 'process'
  * method.
  */

  protected abstract fun doIt ()
}