package catalog;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.logging.Level;

import com.vmware.vcloud.api.rest.schema.ReferenceType;
import com.vmware.vcloud.sdk.Organization;
import com.vmware.vcloud.sdk.VCloudException;
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
  
  public static VcloudClient buildVCloudClient(String vCloudUrl, String username,
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

}
