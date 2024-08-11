package me.john200410.instanceinfo;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.rusherhack.client.api.events.render.EventRender2D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.StringSetting;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Author: John200410
 */
public class InstanceInfoModule extends ToggleableModule {

	/**
	 * Main Settings
	 */
	private final BooleanSetting account = new BooleanSetting("Account", true);
	private final BooleanSetting useCustomAccount = new BooleanSetting("Use Custom Account", false);
	private final StringSetting customAccountName = new StringSetting("Custom Account Name", "Notch");

	private final BooleanSetting server = new BooleanSetting("Server", true);
	private final BooleanSetting useCustomServer = new BooleanSetting("Use Custom Server", false);
	private final StringSetting customServerName = new StringSetting("Custom Server Name", "oldfag.org");

	private final BooleanSetting skinIcon = new BooleanSetting("SkinIcon", true);

	/**
	 * Custom Messages for Server Status
	 */
	private final StringSetting notConnectedMessage = new StringSetting("Not Connected Message", "Not Connected");
	private final StringSetting singleplayerMessage = new StringSetting("Singleplayer Message", "Singleplayer");

	/**
	 * Variables
	 */
	private String cachedAccountName = "";
	private boolean isUsingCustomIcon = false;
	private final Minecraft mc = Minecraft.getInstance();

	public InstanceInfoModule() {
		super("InstanceInfo", "Add additional information to the game window's title", ModuleCategory.CLIENT);

		// Add sub-settings under Account and Server
		this.account.addSubSettings(this.useCustomAccount, this.customAccountName);
		this.server.addSubSettings(this.useCustomServer, this.customServerName);
		this.server.addSubSettings(this.notConnectedMessage, this.singleplayerMessage);

		// Register settings
		this.registerSettings(this.account, this.server, this.skinIcon);
	}

	@Subscribe(stage = Stage.ALL)
	private void onUpdate(EventRender2D event) {
		updateWindowTitle();
	}

	private void updateWindowTitle() {
		final Window window = mc.getWindow();
		final String accountString = getAccountString();

		// Update the icon if necessary
		if (this.skinIcon.getValue()) {
			if (!this.cachedAccountName.equals(accountString)) {
				this.cachedAccountName = accountString;
				this.updateIcon(accountString);
			}
		} else if (this.isUsingCustomIcon) {
			this.setVanillaIcon();
		}

		// Set the title
		window.setTitle(getTitle(accountString));
	}

	private String getAccountString() {
		return useCustomAccount.getValue() ? customAccountName.getValue() : mc.getUser().getName();
	}

	private String getTitle(String accountString) {
		// Determine the correct server string
		final ServerData serverData = mc.getCurrentServer();
		final String serverString;

		if (mc.level == null) {
			serverString = notConnectedMessage.getValue();
		} else if (serverData == null) {
			serverString = singleplayerMessage.getValue();
		} else if (useCustomServer.getValue()) {
			serverString = customServerName.getValue();
		} else {
			serverString = serverData.ip;
		}

		// Build the window title
		StringBuilder titleBuilder = new StringBuilder();

		if (this.account.getValue()) {
			titleBuilder.append(accountString);
		}

		if (this.server.getValue()) {
			if (!titleBuilder.isEmpty()) {
				titleBuilder.append(" - ");
			}
			titleBuilder.append(serverString);
		}

		return titleBuilder.toString();
	}

	@Override
	public void onDisable() {
		if (this.isUsingCustomIcon) {
			this.setVanillaIcon();
		}
	}

	private void updateIcon(String accountString) {
		final String url = String.format("https://mc-heads.net/avatar/%s", accountString);

		NativeImage nativeImage = null;
		MemoryStack memoryStack;
		ArrayList<ByteBuffer> bufferList = new ArrayList<>();
		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
					.build();

			final HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
			nativeImage = NativeImage.read(response.body());
			memoryStack = MemoryStack.stackPush();

			GLFWImage.Buffer buffer = GLFWImage.malloc(1, memoryStack);
			ByteBuffer byteBuffer = MemoryUtil.memAlloc(nativeImage.getWidth() * nativeImage.getHeight() * 4);
			bufferList.add(byteBuffer);
			byteBuffer.asIntBuffer().put(nativeImage.getPixelsRGBA());
			buffer.position(0);
			buffer.width(nativeImage.getWidth());
			buffer.height(nativeImage.getHeight());
			buffer.pixels(byteBuffer);
			GLFW.glfwSetWindowIcon(mc.getWindow().getWindow(), buffer.position(0));

			isUsingCustomIcon = true;
		} catch(Exception exception) {
			System.err.println("An error occurred while updating the icon:");
			exception.printStackTrace(System.err);
		} finally {
			if(nativeImage != null) {
				nativeImage.close();
			}
			bufferList.forEach(MemoryUtil::memFree);
		}
	}

	private void setVanillaIcon() {
		try {
			mc.getWindow().setIcon(mc.getVanillaPackResources(), SharedConstants.getCurrentVersion().isStable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
		} catch(IOException ignored) {
		}
		this.isUsingCustomIcon = false;
	}
}
