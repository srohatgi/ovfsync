package catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.vmware.vcloud.api.rest.schema.CatalogItemType;
import com.vmware.vcloud.api.rest.schema.ComposeVAppParamsType;
import com.vmware.vcloud.api.rest.schema.ReferenceType;
import com.vmware.vcloud.api.rest.schema.SourcedCompositionItemParamType;
import com.vmware.vcloud.api.rest.schema.UploadVAppTemplateParamsType;
import com.vmware.vcloud.sdk.Catalog;
import com.vmware.vcloud.sdk.Organization;
import com.vmware.vcloud.sdk.VCloudException;
import com.vmware.vcloud.sdk.VM;
import com.vmware.vcloud.sdk.Vapp;
import com.vmware.vcloud.sdk.VappTemplate;
import com.vmware.vcloud.sdk.VcloudClient;
import com.vmware.vcloud.sdk.Vdc;
import com.vmware.vcloud.sdk.constants.Version;
import com.vmware.vcloud.sdk.samples.FakeSSLSocketFactory;

import edu.princeton.cs.introcs.StdIn;

/**
 * Upload image templates to an organization catalog
 */
public class Uploader {

  private VcloudClient vcloudClient;
  private Vdc vdc;
  private Organization org;
  private Object catalogRef;
  
  public Uploader(VcloudClient vcloudClient) {
    this.vcloudClient = vcloudClient;
  }

  /**
   * Uploading the ovf package(vapp with 1 blank vm) as a vapp template.
   * 
   * @param name
   * @param desc
   * @param vmdkFilename TODO
   * @param ovfStream
   * @param vmdkStream
   * @return {@link VappTemplate}
   * @throws VCloudException
   * @throws IOException
   * @throws InterruptedException
   */
  public VappTemplate uploadVappTemplate(String name,
      String desc, String vmdkFilename, InputStream ovfStream, InputStream vmdkStream)
      throws VCloudException, IOException, InterruptedException {

    // Creating an vapptemplate with the provided name and description
    UploadVAppTemplateParamsType params = new UploadVAppTemplateParamsType();
    params.setDescription(desc);
    params.setName(name);
    VappTemplate vtmpl = vdc.createVappTemplate(params);

    vtmpl.uploadOVFFile(ovfStream, ovfStream.available());

    // TODO: what's the point of below code??
    vtmpl = VappTemplate.getVappTemplateByReference(vcloudClient,
        vtmpl.getReference());

    // waiting until the vapptemplate gets resolved.
    while (vtmpl.getResource().getStatus() != 8
        && !vtmpl.getResource().isOvfDescriptorUploaded()) {
      System.out.println("Verifying if ovfFile is uploaded..");
      Thread.sleep(500);
    }

    vtmpl.uploadFile(vmdkFilename, vmdkStream, vmdkStream.available());

    // waiting until the vapptemplate gets resolved.
    while (vtmpl.getResource().getStatus() != 8) {
      System.out.println("Verifying if vmdkFile is uploaded..");
      Thread.sleep(500);
    }

    return vtmpl;

  }

  /**
   * Adding the newly created vapptemplate to the catalog.
   * 
   * @param catalogRef
   * @param vAppTemplateName
   * @throws VCloudException
   */
  public void addVappTemplateToCatalog(VappTemplate tmpl, String name, String desc) throws VCloudException {

    if (catalogRef == null) {
      throw new RuntimeException("catalogRef is null");
    }

    CatalogItemType catalogItemType = new CatalogItemType();
    catalogItemType.setName(name);
    catalogItemType.setDescription(desc);
    catalogItemType.setEntity(tmpl.getReference());
    Catalog catalog = Catalog.getCatalogByReference(vcloudClient, (ReferenceType) catalogRef);
    catalog.addCatalogItem(catalogItemType);
  }

  /**
   * Creating Vapp with blank vms.
   * 
   * @param vAppName
   *          :: Vapp Name
   * @param vmName
   *          :: vm Name
   * @param noOfBlankVms
   *          :: no of blank vms needed in the vapp.
   * @throws VCloudException
   * @throws TimeoutException
   */
  private void createVapp(VappTemplate vtmpl, String vAppName, String vmName,
      Integer noOfBlankVms) throws VCloudException, TimeoutException {
    ComposeVAppParamsType composeVAppParamsType = new ComposeVAppParamsType();
    composeVAppParamsType.setName(vAppName);
    composeVAppParamsType.setDescription(vAppName);
    composeVAppParamsType.setPowerOn(false);
    composeVAppParamsType.setDeploy(false);

    // adding the specified no of blank vms.
    for (int i = 0; i < noOfBlankVms; i++) {
      ReferenceType vappTemplateRef = new ReferenceType();
      vappTemplateRef.setName(vmName + "-" + i);
      vappTemplateRef.setHref(vtmpl.getChildren().get(0).getReference()
          .getHref());
      SourcedCompositionItemParamType vappTemplateItem = new SourcedCompositionItemParamType();
      vappTemplateItem.setSource(vappTemplateRef);
      composeVAppParamsType.getSourcedItem().add(vappTemplateItem);
    }

    Vapp vapp = vdc.composeVapp(composeVAppParamsType);
    vapp.getTasks().get(0).waitForTask(0);
    vapp = Vapp.getVappByReference(vcloudClient, vapp.getReference());

  }

  /**
   * main
   * 
   * @param args
   * @throws VCloudException
   * @throws KeyManagementException
   * @throws NoSuchAlgorithmException
   * @throws IOException
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws TimeoutException
   * @throws UnrecoverableKeyException
   * @throws KeyStoreException
   * @throws InterruptedException
   */
  public static void main(String args[]) {

    if (args.length < 6) {
      System.err.println("USAGE");
      System.err.println("-----");
      System.err
          .println("Uploader <username> <password> <orgName> <vdcName> <existingCatalogName>");
      System.exit(1);
    }

    String vCloudUrl = args[0];
    String username = args[1];
    String password = args[2];

    String orgName = args[3];
    String vdcName = args[4];
    String catalogName = args[5];

    // testing args
    vCloudUrl = "https://uklo1.symphonycloud.savvis.com";
    orgName = "Symphony1";
    vdcName = "SS_DEMO_6_UK";
    catalogName = "wp";

    System.out.println("Blank VM's Sample");
    System.out.println("-----------------");
    
    try {
      VcloudClient vc = buildVCloudClient(vCloudUrl, username, password);
      
      Uploader u = new Uploader(vc);
      
      u.setupVCloudParams(orgName, vdcName, catalogName);

      // upload vapptemplate
      while (StdIn.hasNextLine()) {
        String[] vals = StdIn.readLine().split(" ");
        String name = vals[0];
        String ovfLoc = vals[1];
        String vmdkLoc = vals[2];
        String desc = vals[3];

        InputStream ovfStream = null, vmdkStream = null;
        
        try {
          ovfStream = new FileInputStream(new File(ovfLoc));
          vmdkStream = new FileInputStream(new File(vmdkLoc));
          VappTemplate vtmpl = u.uploadVappTemplate(name, desc, vmdkLoc, ovfStream, vmdkStream);
          u.addVappTemplateToCatalog(vtmpl, name, desc);        
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

  private void setupVCloudParams(String orgName, String vdcName,
      String catalogName) throws KeyManagementException,
      UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
    catalogRef = null;

    try {

      // find org
      org = Organization.getOrganizationByReference(vcloudClient,
          vcloudClient.getOrgRefByName(orgName));
      System.out.println("Organization :: " + org.getReference().getName());
  
      // find vdc
      vdc = Vdc.getVdcByReference(vcloudClient, org.getVdcRefByName(vdcName));
      System.out.println("Vdc :: " + vdc.getReference().getName());
  
      // find catalog
      for (ReferenceType reference : org.getCatalogRefs()) {
        if (reference.getName().equals(catalogName)) {
          catalogRef = reference;
          break;
        }
      }
      
    } catch (VCloudException vcex) {
      System.err.println("Error communicating:" + vcex.getMessage());
      System.exit(1);
    }
  }

  public static VcloudClient buildVCloudClient(String vCloudUrl, String username,
      String password) throws KeyManagementException,
      UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
      VCloudException {

    VcloudClient vcloudClient = new VcloudClient(vCloudUrl, Version.V5_1);

    // change log levels if needed.
    VcloudClient.setLogLevel(Level.ALL);
    vcloudClient.registerScheme("https", 443, FakeSSLSocketFactory.getInstance());
    //System.out.println("---------" + username + password);

    vcloudClient.login(username, password);
    return vcloudClient;
  }
}
