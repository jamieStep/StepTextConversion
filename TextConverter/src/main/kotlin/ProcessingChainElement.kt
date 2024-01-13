package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor

/******************************************************************************/
/**
 * Base class for main processors.
 *
 * @author ARA "Jamie" Jamieson
 */

interface ProcessingChainElement
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
   * Returns details of any command-line parameters this processor requires or permits.
   *
   * @param commandLineProcessor Command line processor.
   */

  fun getCommandLineOptions (commandLineProcessor: CommandLineProcessor)



  /****************************************************************************/
  /**
  * Carries out any preparation -- for example clearing out folders.
  */

  fun pre ()


  /****************************************************************************/
  /**
   * The processing method.
   */

  fun process ()


  /****************************************************************************/
  /* The folder from which the processing takes its input, and the pattern-match
     for input files. */

  fun takesInputFrom (): Pair<String, String>
}
