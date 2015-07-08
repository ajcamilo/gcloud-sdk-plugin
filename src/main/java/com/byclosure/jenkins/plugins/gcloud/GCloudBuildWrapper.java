package com.byclosure.jenkins.plugins.gcloud;

import com.cloudbees.jenkins.plugins.gcloudsdk.GCloudInstallation;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.jenkins.plugins.credentials.domains.RequiresDomain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

@RequiresDomain(value = GCloudScopeRequirement.class)
public class GCloudBuildWrapper extends BuildWrapper {
	private static final Logger LOGGER = Logger.getLogger(GCloudBuildWrapper.class.getName());

	private final String installation;
	private final String credentialsId;

	@DataBoundConstructor
	public GCloudBuildWrapper(String installation, String credentialsId) {
        this.installation = installation;
        this.credentialsId = credentialsId;
    }

	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        final ToolInstallation sdk = getSDK().translate(build, listener);
        final FilePath configDir = build.getWorkspace().createTempDir("gcloud", "config");

		final GCloudServiceAccount serviceAccount =
				GCloudServiceAccount.getServiceAccount(build, launcher, listener, credentialsId, configDir);

		if (!serviceAccount.activate()) {
			serviceAccount.cleanUp();
			throw new InterruptedException("Couldn't activate GCloudServiceAccount");
		}

		return new Environment() {
			@Override
			public void buildEnvVars(Map<String, String> env) {
			}

			@Override
			public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException, InterruptedException {
				if (!serviceAccount.revoke()) {
					serviceAccount.cleanUp();
					return false;
				}

				serviceAccount.cleanUp();
				return true;
			}

		};
	}

    public GCloudInstallation getSDK() {
        GCloudInstallation[] installations = GCloudInstallation.getInstallations();
        if (installations.length == 1) return installations[0];
        for (GCloudInstallation sdk : installations) {
            if (installation.equals(sdk.getName())) return sdk;
        }
        throw new IllegalArgumentException("Invalid GCloud SDK installation "+installation);
    }



    public String getInstallation() {
        return installation;
    }

    public String getCredentialsId() {
		return credentialsId;
	}

	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		public DescriptorImpl() {
			load();
		}

		@Override
		public String getDisplayName() {
			return "GCloud authentication";
		}

		@Override
		public boolean isApplicable(AbstractProject item) {
			return true;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			save();
			return super.configure(req, formData);
		}

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
                                                     @QueryParameter String serverAddress) {
            if (project == null || !project.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            MATCHER,
                            CredentialsProvider.lookupCredentials(GoogleRobotPrivateKeyCredentials.class,
                                    project,
                                    ACL.SYSTEM,
                                    URIRequirementBuilder.fromUri(serverAddress).build()));
        }

        public static final CredentialsMatcher MATCHER = CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(GoogleRobotPrivateKeyCredentials.class));
	}
}
