/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.stepexception.StepException



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  Dbg.setBooksToBeProcessed("Mat")

  try
  {
    mainCommon(args)
    if (!ConfigData["stepReversificationDataLocation"]!!.startsWith("http")) println(C_Local_ReversificationData)
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) println(C_NotEncrypted)
    Dbg.endOfRun()
    println("Finished\n")
  }
  catch (e: StepException)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    if (!e.getSuppressStackTrace()) e.printStackTrace()
  }
  catch (e: Exception)
  {
    Dbg.endOfRun()
    if (null != e.message) println(e.message)
    e.printStackTrace()
  }
}


/******************************************************************************/
private fun mainCommon (args: Array<String>)
{
  TextConverterController().process(args)
  TestController.instance().terminate()
  Logger.summariseResults()
}


/******************************************************************************/
private const val C_Local_ReversificationData ="""
     #        #######   #####      #     #            ######   #######  #     #  #######  ######    #####   ###  #######  ###   #####      #     #######  ###  #######  #     #      ######      #     #######     #    
     #        #     #  #     #    # #    #            #     #  #        #     #  #        #     #  #     #   #   #         #   #     #    # #       #      #   #     #  ##    #      #     #    # #       #       # #   
     #        #     #  #         #   #   #            #     #  #        #     #  #        #     #  #         #   #         #   #         #   #      #      #   #     #  # #   #      #     #   #   #      #      #   #  
     #        #     #  #        #     #  #            ######   #####    #     #  #####    ######    #####    #   #####     #   #        #     #     #      #   #     #  #  #  #      #     #  #     #     #     #     # 
     #        #     #  #        #######  #            #   #    #         #   #   #        #   #          #   #   #         #   #        #######     #      #   #     #  #   # #      #     #  #######     #     ####### 
     #        #     #  #     #  #     #  #            #    #   #          # #    #        #    #   #     #   #   #         #   #     #  #     #     #      #   #     #  #    ##      #     #  #     #     #     #     # 
     #######  #######   #####   #     #  #######      #     #  #######     #     #######  #     #   #####   ###  #        ###   #####   #     #     #     ###  #######  #     #      ######   #     #     #     #     # 
                                                                                                                                                                                                                    
     """


/******************************************************************************/
private const val C_NotEncrypted = """
      _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
     | \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
     |  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
     | |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
     |_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                     
     """
