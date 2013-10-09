package catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import com.vmware.vcloud.api.rest.schema.ReferenceType;
import com.vmware.vcloud.sdk.VappTemplate;
import com.vmware.vcloud.sdk.Vdc;

import edu.princeton.cs.introcs.StdIn;

/**
 * Upload image templates to an organization catalog
 */
public class Uploader {
  Vdc vdc;
  ReferenceType catalogRef;
  
  public Uploader() { }





  /**
   * main
   * 
   * @param args
   */
  public static void main(String args[]) {

    if (args.length < 6) {
      System.err.println("USAGE");
      System.err.println("-----");
      System.err
          .println("Uploader <vcloudUrl> <username> <password> <orgName> <vdcName> <existingCatalogName>");
      System.exit(1);
    }

    String vCloudUrl = args[0];
    String username = args[1];
    String password = args[2];

    String orgName = args[3];
    String vdcName = args[4];
    String catalogName = args[5];

    /* testing args
    vCloudUrl = "https://uklo1.symphonycloud.savvis.com";
    orgName = "Symphony1";
    vdcName = "SS_DEMO_6_UK";
    catalogName = "wp";
    */
    
    try {
      VcloudAdapter vca = new VcloudAdapter(vCloudUrl, username, password);
      
      vca.setupVCloudParams(orgName, vdcName, catalogName);
      
      Uploader u = new Uploader();
      u.vdc = vca.getVdc();
      u.catalogRef = vca.getCatalogRef();

      // upload vapptemplate
      while (StdIn.hasNextLine()) {
        String[] vals = StdIn.readLine().split(" ");
        String name = vals[0];
        String ovfLoc = vals[1];
        String vmdkLoc = vals[2];
        String desc = vals[3];
        String vmdkFileName = vmdkLoc.substring(vmdkLoc.lastIndexOf("/")+1);

        System.out.println("tmpl name:"+name+" ovfLoc:"+ovfLoc+" vmdkLoc:"+vmdkLoc+" vmdkFileName:"+vmdkFileName+" desc:"+desc);

        InputStream ovfStream = null, vmdkStream = null;
        
        try {
          ovfStream = new FileInputStream(new File(ovfLoc));
          vmdkStream = new FileInputStream(new File(vmdkLoc));
          VappTemplate vtmpl = vca.uploadVappTemplate(name, desc, 
        		  vmdkFileName, ovfStream, vmdkStream);
          vca.addVappTemplateToCatalog(vtmpl, name, desc);        
        } finally {
          if (ovfStream!=null)
            ovfStream.close();
          if (vmdkStream!=null)
            vmdkStream.close();
        }
        
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("Error");
      System.exit(1);
    } 

    /****
     * VERIFICATION // creating vapp with the specified no of blank vm's.
     *
     * String vAppTemplateName = "satya_demo_template";
     * String vAppName = "satya_test_vapp";
     * String vmName = "satyarhel";
     * Integer noOfBlankVms = 1;
     * createVapp(vAppName, vmName, noOfBlankVms);
     *
     * System.out.println("Created Vapp :: " + vapp.getReference().getName());
     * System.out.println("		VM Names"); 
     * System.out.println("		--------"); 
     * for(VM vm : vapp.getChildrenVms()) { 
     * System.out.println("		" + vm.getResource().getName()); }
     */
  }


}
