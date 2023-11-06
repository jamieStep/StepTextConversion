/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.support.stepexception.StepException
import java.io.File
import java.net.URLClassLoader



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  Dbg.setBooksToBeProcessed("Gal,Nam,Sng")

  try
  {
    test()
    mainCommon(args)
    val majorWarnings = GeneralEnvironmentHandler.getMajorWarningsAsBigCharacters()
    if (majorWarnings.isNotEmpty())
    {
      print(majorWarnings)
      Logger.warning(majorWarnings)
      Logger.announceAll(false);
    }


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
private fun test ()
{
  return
    val jarFile = File("C:\\Users\\Jamie\\RemotelyBackedUp\\Git\\StepTextConversion\\Texts\\Dbl\\Lockman\\Text_eng_LSB\\Preprocessor\\LockmanPreprocessor.jar")
    val jarURL = jarFile.toURI().toURL()
    val classLoader = URLClassLoader(arrayOf(jarURL), Thread.currentThread().contextClassLoader)

    try
    {
        // Load the class dynamically
        val loadedClass = classLoader.loadClass("org.stepbible.preprocessor.Preprocessor")

        // Create an instance of the loaded class
        val instance = loadedClass.getDeclaredConstructor().newInstance()
        val classToLoad = Class.forName("org.stepbible.preprocessor.Preprocessor", true, classLoader)
        val method = classToLoad.getDeclaredMethod("preprocess", String::class.java)
        method.invoke(instance, "Hi there")
        println(instance::class.simpleName)
        println(instance::class.qualifiedName)
    }
    catch (e: Exception)
    {
      println("Oops")
    }
}
