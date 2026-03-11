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
 * This OSIS needs to be as faithful as possible a representation of the input
 * data -- ie as few modifications as possible should be applied.  The resulting
 * OSIS should be stored in the InputOsis folder, under the name DONT_USE_ME.xml.
 * The file is renamed to something more meaningful later in the processing when
 * we know things have worked.
 *
 * (Builder_InitialOsisRepresentationFromOsis merely creates a copy of its input.
 * The other Builder_InitialOsisRepresentationFrom* classes actually *do*
 * something to convert their inputs to OSIS.
 *
 * Which of these we need to run depends upon what kinds of input are available,
 * and whether we have been told to use OSIS in preference to anything else.
 * This call is made by [Builder_InitialOsisRepresentationOfInputs], which works
 * out what to do and then calls the appropriate one of the
 * Builder_InitialOsisRepresentationFrom... objects to do the work.
 *
 * [Builder_InternalOsis] sets aside a copy of the OSIS for possible use for
 * things like tagging.  (This I refer to as 'external OSIS'.)
 *
 * It then makes additional ad hoc modifications to the OSIS to avoid any
 * known rendering problems in STEPBible.  This version of OSIS I refer to
 * as 'internal OSIS'.  These changes tend to be ad hoc in nature, which is
 * why I separate the external and internal OSIS.  If you are doing things
 * like applying tagging, you probably want a version of the OSIS which is
 * relatively stable, and which isn't going to be changed merely because we
 * discover some issue with the rendering which we need to work around.  In
 * theory the internal OSIS is of no lasting value, and probably ought to be
 * deleted to avoid confusion.  At present, though, I am retaining it for
 * debugging purposes.
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

  fun process () = doIt()





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