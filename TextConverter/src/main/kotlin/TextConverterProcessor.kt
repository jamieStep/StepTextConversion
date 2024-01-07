package org.stepbible.textconverter

import org.stepbible.textconverter.support.commandlineprocessor.CommandLineProcessor

/******************************************************************************/
/**
 * Base class for main processors.
 *
 * @author ARA "Jamie" Jamieson
 */

interface TextConverterProcessor
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

  fun prepare ()


  /****************************************************************************/
  /**
   * The processing method.
   */

  fun process ()
}


/******************************************************************************/
/**
* Adds input selection to TextConverterController -- see setInputSelector
* for details.
*
* (I'd really like to bake this into [TextConverterController], but a property
* such as m_InputSelector has to be defined in the implementing class, and
* there are a lot of classes derived from TextConverterController, most of which
* don't need this, and I don't want bother adding a pointless variable to each
* one of them.
*/

interface TextConverterProcessorWithInputSelector
{
  /****************************************************************************/
  /**
   * Some derived classes can take input from either one folder or another,
   * depending upon the circumstances.  This tells them where to look.
   * Derived classes which aren't interested should simply ignore this setting.
   *
   * @param inputSelector The required setting.
   */

  fun setInputSelector (inputSelector: String)
  {
    m_InputSelector = inputSelector
  }



  /****************************************************************************/
  var m_InputSelector: String
}
