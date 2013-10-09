package catalog;

import com.vmware.vcloud.api.rest.schema.CloneVAppTemplateParamsType;
import com.vmware.vcloud.api.rest.schema.ReferenceType;
import com.vmware.vcloud.sdk.Catalog;
import com.vmware.vcloud.sdk.Organization;
import com.vmware.vcloud.sdk.VCloudException;
import com.vmware.vcloud.sdk.VcloudClient;
import com.vmware.vcloud.sdk.Vdc;

import edu.princeton.cs.introcs.StdIn;

/**
 * Upload image templates to an organization catalog
 */
public class CloneTemplate {
  
  ReferenceType catalogRef;
  static VcloudClient vcloudClient;
  
  public CloneTemplate() { }





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
      vcloudClient = vca.getVcloudClient();

      
      // upload vapptemplate
      while (StdIn.hasNextLine()) {
        String[] vals = StdIn.readLine().split(" ");
        String name = vals[0];
        String destinationCatalog = vals[1];

        // find the vapp template ref
        ReferenceType soruceVappTemplateRef = findVappTemplateRef(orgName, vdcName, name);
        CloneVAppTemplateParamsType cloneParams = new CloneVAppTemplateParamsType();
        cloneParams.setDescription("public tempalte");
        cloneParams.setName(name);
        cloneParams.setSource(soruceVappTemplateRef);
        
        Catalog destinationCatalogRef = searchCatalog(orgName, destinationCatalog);
        
        Vdc vdc = vca.getVdc();
        vdc.cloneVappTemplate(cloneParams, destinationCatalogRef.getReference());
        
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
  
  
  public static ReferenceType findVappTemplateRef(String orgName,
      String vdcName, String vappTemplateName) throws VCloudException {
    
    ReferenceType orgRef = vcloudClient.getOrgRefsByName().get(orgName);
    Organization org = Organization.getOrganizationByReference(vcloudClient, orgRef);
    ReferenceType vdcRef = org.getVdcRefByName(vdcName);
    Vdc vdc = Vdc.getVdcByReference(vcloudClient, vdcRef);
    for (ReferenceType vappTemplateRef : vdc.getVappTemplateRefs())
      if (vappTemplateRef.getName().equals(vappTemplateName)) {
        System.out.println("Vapp Template: " + vappTemplateName);
        return vappTemplateRef;
      }
    return null;
  }

  /**
   * Search the catalog
   *
   * @param orgName
   * @param catalogName
   * @return
   * @throws VCloudException
   */
  private static Catalog searchCatalog(String orgName, String catalogName)
      throws VCloudException {
    ReferenceType orgRef = vcloudClient.getOrgRefsByName().get(orgName);
    Organization org = Organization.getOrganizationByReference(vcloudClient,
        orgRef);
    ReferenceType catalogRef = null;
    for (ReferenceType ref : org.getCatalogRefs()) {
      if (ref.getName().equals(catalogName))
        catalogRef = ref;
    }
    return Catalog.getCatalogByReference(vcloudClient, catalogRef);
  }

}
