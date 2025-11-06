
using HarmonyLib;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;



  public  class GetEntryAssembly
    {
        public static void GetEntryAssemblyPatch()
        {
            Harmony harmony = new Harmony("com.example.2");
            MethodInfo original = AccessTools.Method(typeof(Assembly), "GetEntryAssembly");
            HarmonyMethod prefix = new HarmonyMethod(typeof(GetEntryAssembly), "GetEntryAssembly_Prefix");
            harmony.Patch(original, prefix);
            Console.WriteLine("GetEntryAssembly Prefix applied!");
        }


        // This method will run before the original method and modify its result
        public static bool GetEntryAssembly_Prefix(ref Assembly __result)
        {
            try
            {
                // Here, we will modify the result returned by GetEntryAssembly
                // We will return the Executing Assembly instead of the default logic
                __result = Assembly.GetExecutingAssembly();
                Console.WriteLine("Assembly.GetExecutingAssembly() is now returned instead of the default GetEntryAssembly.");
                return false; // Skip the original method since we have modified the result
            }
            catch (Exception ex)
            {
                Console.WriteLine("GetEntryAssembly_Prefix encountered an error:");
                Console.WriteLine("Exception Message: " + ex.Message);
                Console.WriteLine("Stack Trace: " + ex.StackTrace);
                return true; // Return true to continue with the original method in case of an error
            }
        }
    }


