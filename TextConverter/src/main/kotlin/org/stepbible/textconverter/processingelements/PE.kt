package org.stepbible.textconverter.processingelements

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor

/******************************************************************************/
/**
 * Base class for main processors.
 *
 * @author ARA "Jamie" Jamieson
 */

interface PE
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
}
