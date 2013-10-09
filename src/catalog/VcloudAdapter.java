package catalog;

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
import com.vmware.vcloud.sdk.Vapp;
import com.vmware.vcloud.sdk.VappTemplate;
import com.vmware.vcloud.sdk.VcloudClient;
import com.vmware.vcloud.sdk.Vdc;
import com.vmware.vcloud.sdk.constants.Version;
import com.vmware.vcloud.sdk.samples.FakeSSLSocketFactory;

public class VcloudAdapter {
  private Vdc vdc;
  private Organization org;
  private ReferenceType catalogRef;
  private VcloudClient vcloudClient;
  
  public VcloudClient getVcloudClient() {
    return vcloudClient;
  }

  public Vdc getVdc() {
    return vdc;
  }

  public Organization getOrg() {
    return org;
  }

  public ReferenceType getCatalogRef() {
    return catalogRef;
  }
  
  public VcloudAdapter(String vCloudUrl, String username,
  String password) throws KeyManagementException,
  UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
  VCloudException {
    vcloudClient = buildVCloudClient(vCloudUrl, username, password);
  }

  public void setupVCloudParams(String orgName, String vdcName,
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

  
  private static VcloudClient buildVCloudClient(String vCloudUrl, String username,
      String password) throws KeyManagementException,
      UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
      VCloudException {
  
    VcloudClient vcloudClient = new VcloudClient(vCloudUrl, Version.V5_1);
  
    // change log levels if needed.
    VcloudClient.setLogLevel(Level.WARNING);
    vcloudClient.registerScheme("https", 443, FakeSSLSocketFactory.getInstance());
    //System.out.println("---------" + username + password);
  
    vcloudClient.login(username, password);
    return vcloudClient;
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
  public void createVapp(VappTemplate vtmpl, String vAppName, String vmName,
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
    System.out.println("STARTING to upload ovfFile");
    
    while (!vtmpl.getResource().isOvfDescriptorUploaded()) {
      vtmpl = VappTemplate.getVappTemplateByReference(vcloudClient, vtmpl.getReference());
      System.out.println("  waiting for ovfFile to be processed...");
      Thread.sleep(500);
    }
    
    System.out.println("FINISHED upload ovfFile");
    
    
    System.out.println("STARTING to upload vmdkFile");
    vtmpl.uploadFile(vmdkFilename, vmdkStream, vmdkStream.available());

    // waiting until the vapptemplate gets resolved.
    while (vtmpl.getResource().getStatus() != 8) {
      vtmpl = VappTemplate.getVappTemplateByReference(vcloudClient, vtmpl.getReference());
      System.out.println("  waiting for vmdkFile  to be processed...");
      Thread.sleep(500);
    }
    System.out.println("FINISHED upload vmdkFile");

    return vtmpl;

  }
}
