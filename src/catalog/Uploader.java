package catalog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
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

/**
 * 1. UPLOAD VAPPTEMPLATE :: Uploads the selfcontained ovf xml which contains a
 * vapp and a blank vm(1 vcpu and 512 mb) with no disks/nics.
 * 
 * 2. ADD VAPP TEMPLATE TO CATALOG :: Attaches the uploaded vapptemplate to a
 * catalog.
 * 
 * 3. COMPOSE NEW VAPP :: Composes a vapp (with 'n' Blank VM's) using the newly
 * uploaded vapptemplate.
 * 
 * @author Ecosystem Engineering
 */

public class Uploader {

  private static VcloudClient vcloudClient;
  private static Vdc vdc;
  private static VappTemplate vappTemplate;
  private static Vapp vapp;

  private static String imageDir = "src/main/resources";
  private static String nameOvfRhel = "rhel6.3_64.ovf";
  private static String nameOvfBlankVm = "blankVM.ovf";
  private static String vmdkfileName = "rhel6.3_64-disk1.vmdk";

  /**
   * Uploading the ovf package(vapp with 1 blank vm) as a vapp template.
   * 
   * @param vAppTemplateName
   * @param vAppTemplateDesc
   * @param ovfStream
   * @param vmdkStream
   *          TODO
   * @return {@link VappTemplate}
   * @throws VCloudException
   * @throws IOException
   * @throws InterruptedException
   */
  public static VappTemplate uploadVappTemplate(String vAppTemplateName,
      String vAppTemplateDesc, InputStream ovfStream, InputStream vmdkStream)
      throws VCloudException, IOException, InterruptedException {

    // Creating an vapptemplate with the provided name and description
    UploadVAppTemplateParamsType vappTemplParams = new UploadVAppTemplateParamsType();
    vappTemplParams.setDescription(vAppTemplateDesc);
    vappTemplParams.setName(vAppTemplateName);
    VappTemplate vappTemplate = vdc.createVappTemplate(vappTemplParams);
    /*
     * String ovfFileLocation = imageDir + "/" + nameOvfRhel; File ovfFile = new
     * File(ovfFileLocation); FileInputStream ovfFileInputStream = new
     * FileInputStream(ovfFile); vappTemplate.uploadOVFFile(ovfFileInputStream,
     * ovfFile.length());
     */

    vappTemplate.uploadOVFFile(ovfStream, ovfStream.available());

    vappTemplate = VappTemplate.getVappTemplateByReference(vcloudClient,
        vappTemplate.getReference());
    // waiting until the ovf descriptor uplaoded flag is true.
    while (!vappTemplate.getResource().isOvfDescriptorUploaded()) {
      vappTemplate = VappTemplate.getVappTemplateByReference(vcloudClient,
          vappTemplate.getReference());
    }

    // waiting until the vapptemplate gets resolved.
    while (vappTemplate.getResource().getStatus() != 8
        && !vappTemplate.getResource().isOvfDescriptorUploaded()) {
      System.out.println("Verifying if ovfFile is uploaded..");
      Thread.sleep(500);
    }

    ovfStream.close();

    /*
     * String vmdkFileLocation = imageDir + "/" + vmdkfileName; File vmdkFile =
     * new File(vmdkFileLocation); FileInputStream vmdkFileInputStream = new
     * FileInputStream(vmdkFile); vappTemplate.uploadFile(vmdkfileName,
     * vmdkFileInputStream, vmdkFile.length());
     */

    vappTemplate.uploadFile(vmdkfileName, vmdkStream, vmdkStream.available());
    return vappTemplate;

  }

  /**
   * Adding the newly created vapptemplate to the catalog.
   * 
   * @param catalogRef
   * @param vAppTemplateName
   * @throws VCloudException
   */
  private static void addVappTemplateToCatalog(ReferenceType catalogRef,
      String vAppTemplateName) throws VCloudException {
    if (catalogRef == null) {
      System.out.println("Catalog not found");
      System.exit(0);
    }
    CatalogItemType catalogItemType = new CatalogItemType();
    catalogItemType.setName(vAppTemplateName);
    catalogItemType.setDescription(vAppTemplateName);
    catalogItemType.setEntity(vappTemplate.getReference());
    Catalog catalog = Catalog.getCatalogByReference(vcloudClient, catalogRef);
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
  private static void createVapp(String vAppName, String vmName,
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
      vappTemplateRef.setHref(vappTemplate.getChildren().get(0).getReference()
          .getHref());
      SourcedCompositionItemParamType vappTemplateItem = new SourcedCompositionItemParamType();
      vappTemplateItem.setSource(vappTemplateRef);
      composeVAppParamsType.getSourcedItem().add(vappTemplateItem);
    }

    vapp = vdc.composeVapp(composeVAppParamsType);
    vapp.getTasks().get(0).waitForTask(0);
    vapp = Vapp.getVappByReference(vcloudClient, vapp.getReference());

  }

  /**
   * Creating Vapp with 'n' blank vm's sample.
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
  public static void main(String args[]) throws VCloudException,
      KeyManagementException, NoSuchAlgorithmException, IOException,
      InstantiationException, IllegalAccessException, TimeoutException,
      UnrecoverableKeyException, KeyStoreException, InterruptedException {

    if (args.length < 6) {
      System.err.println("USAGE");
      System.err.println("-----");
      System.err
          .println("vcloudUrl username password orgName vdcName existingCatalogName");
      System.exit(0);
    }

    String vCloudUrl = args[0];
    String username = args[1];
    String password = args[2];

    String orgName = args[3];
    String vdcName = args[4];
    String catalogName = args[5];

    // default args
    vCloudUrl = "https://uklo1.symphonycloud.savvis.com";
    orgName = "Symphony1";
    vdcName = "SS_DEMO_6_UK";
    catalogName = "wp";

    String vAppTemplateName = "satya_demo_template";
    String vAppName = "satya_test_vapp";
    String vmName = "satyarhel";
    Integer noOfBlankVms = 1;

    System.out.println("Blank VM's Sample");
    System.out.println("-----------------");

    vcloudClient = new VcloudClient(vCloudUrl, Version.V5_1);
    // change log levels if needed.
    VcloudClient.setLogLevel(Level.ALL);
    vcloudClient.registerScheme("https", 443,
        FakeSSLSocketFactory.getInstance());
    System.out.println("---------" + username + password);
    vcloudClient.login(username, password);

    // find org
    Organization org = Organization.getOrganizationByReference(vcloudClient,
        vcloudClient.getOrgRefByName(orgName));
    System.out.println("Organization :: " + org.getReference().getName());

    // find vdc
    vdc = Vdc.getVdcByReference(vcloudClient, org.getVdcRefByName(vdcName));
    System.out.println("Vdc :: " + vdc.getReference().getName());

    // upload vapptemplate

    vappTemplate = uploadVappTemplate(vAppTemplateName, vAppTemplateName,
        new ByteArrayInputStream(null), null);
    System.out.println("Created VappTemplate :: " + vAppTemplateName);

    // add to catalog
    ReferenceType catalogRef = null;
    for (ReferenceType reference : org.getCatalogRefs()) {
      if (reference.getName().equals(catalogName)) {
        catalogRef = reference;
        break;
      }
    }

    addVappTemplateToCatalog(catalogRef, vAppTemplateName);
    System.out.println("Added VappTemplate to Catalog :: "
        + catalogRef.getName());

    /****
     * VERIFICATION // creating vapp with the specified no of blank vm's.
     * createVapp(vAppName, vmName, noOfBlankVms);
     * System.out.println("Created Vapp :: " + vapp.getReference().getName());
     * System.out.println("		VM Names"); System.out.println("		--------"); for
     * (VM vm : vapp.getChildrenVms()) { System.out.println("		" +
     * vm.getResource().getName()); }
     */
  }
}
