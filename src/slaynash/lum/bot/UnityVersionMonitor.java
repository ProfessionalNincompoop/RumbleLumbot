package slaynash.lum.bot;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import mono.cecil.AssemblyDefinition;
import mono.cecil.FieldDefinition;
import mono.cecil.MethodDefinition;
import mono.cecil.ModuleDefinition;
import mono.cecil.ParameterDefinition;
import mono.cecil.ReaderParameters;
import mono.cecil.ReadingMode;
import mono.cecil.TypeDefinition;
import net.dv8tion.jda.api.utils.FileUpload;
import slaynash.lum.bot.discord.JDAManager;
import slaynash.lum.bot.utils.ExceptionUtils;
import slaynash.lum.bot.utils.Utils;

public class UnityVersionMonitor {
    public static final String LOG_IDENTIFIER = "UnityVersionMonitor";

    private static final String hrefIdentifier = "<a href=\"https://download.unity3d.com/";

    private static final String downloadPath = "/mnt/hdd3t/unity_versions";

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final List<UnityVersion> unityVersions = new ArrayList<>();

    private static final Map<String, List<String>> installedVersions = new HashMap<>();

    private static final List<UnityICall> icalls = new ArrayList<>();

    private static final List<MonoStructInfo> monoStructs = new ArrayList<>() {
        {
            add(new MonoStructInfo("UnityEngine.Internal_DrawTextureArguments", "UnityEngine.CoreModule"));
            add(new MonoStructInfo("UnityEngine.Rendering.VertexAttribute", "UnityEngine.CoreModule",
                new MonoStructInfo("UnityEngine.Mesh/InternalShaderChannel", "UnityEngine.CoreModule")));
            add(new MonoStructInfo("UnityEngine.UIVertex", "UnityEngine.TextRenderingModule"));
        }
    };

    private static boolean initialisingUnityVersions = false;
    private static boolean isRunningCheck = false;

    public static void start() {
        if (!ConfigManager.mainBot)
            return;

        loadInstalledVersionCache();
        loadMonoStructCache();
        loadIcalls();

        Thread thread = new Thread(() -> {

            boolean firstRun = true;

            while (true) {

                if (firstRun)
                    firstRun = false;
                else
                    try {
                        Thread.sleep(60 * 60 * 1000);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                try {
                    // fetch unity versions

                    HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("https://unity3d.com/get-unity/download/archive"))
                        .setHeader("User-Agent", "LUM Bot")
                        .timeout(Duration.ofSeconds(30))
                        .build();

                    String pagedata;

                    try {
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        pagedata = response.body();
                    }
                    catch (Exception e) {
                        ExceptionUtils.reportException("Failed to fetch Unity versions", e);
                        continue;
                    }

                    String[] pageLines = pagedata.split("[\r\n]");
                    for (String line : pageLines) {
                        if (line.isEmpty() || line.contains("Samsung"))
                            continue;

                        int hrefIdentifierIndex;
                        if ((hrefIdentifierIndex = line.indexOf(hrefIdentifier)) < 0)
                            continue;

                        String setupIdentifier = "UnitySetup64-";
                        if (!line.contains(setupIdentifier) && !line.contains(setupIdentifier = "UnitySetup-"))
                            continue;

                        String subline = line.substring(hrefIdentifierIndex + hrefIdentifier.length());
                        String foundUrl;
                        int extensionIndex;
                        if ((extensionIndex = subline.indexOf(".exe")) < 0)
                            continue;

                        String foundVersion = subline.substring(0, extensionIndex);
                        foundVersion = foundVersion.substring(foundVersion.lastIndexOf(setupIdentifier) + setupIdentifier.length());

                        String fullVersion = foundVersion;
                        if (foundVersion.contains("f"))
                            foundVersion = foundVersion.split("f")[0];

                        String urlIl2CppWin = null;
                        /*
                        if (foundVersion.startsWith("20")) {
                            String versionId = subline.split("/")[1];
                            if (foundVersion.startsWith("2017"))
                                foundUrl = "https://download.unity3d.com/download_unity/" + versionId + "/MacEditorTargetInstaller/UnitySetup-Windows-Support-for-Editor-" + fullVersion + ".pkg";
                            else
                                foundUrl = "https://download.unity3d.com/download_unity/" + versionId + "/MacEditorTargetInstaller/UnitySetup-Windows-Mono-Support-for-Editor-" + fullVersion + ".pkg";

                            if (!foundVersion.startsWith("2017"))
                                urlIl2CppWin = "https://download.unity3d.com/download_unity/" + versionId + "/TargetSupportInstaller/UnitySetup-Windows-IL2CPP-Support-for-Editor-" + fullVersion + ".exe";
                        }

                        if (foundVersion.startsWith("5.3")) // We don't care about versions earlier than 5.4.0
                            break;
                        */

                        if (foundVersion.startsWith("20")) {
                            if (foundVersion.startsWith("2017.1"))
                                break;

                            String versionId = subline.split("/")[1];

                            if (foundVersion.startsWith("2017.2")) {
                                foundUrl = "https://beta.unity3d.com/download/" + versionId + "/MacEditorTargetInstaller/UnitySetup-Windows-Support-for-Editor-" + fullVersion + ".pkg";
                            }
                            else if (foundVersion.startsWith("2017")) {
                                foundUrl = "https://download.unity3d.com/download_unity/" + versionId + "/MacEditorTargetInstaller/UnitySetup-Windows-Support-for-Editor-" + fullVersion + ".pkg";
                            }
                            else {
                                foundUrl = "https://download.unity3d.com/download_unity/" + versionId + "/MacEditorTargetInstaller/UnitySetup-Windows-Mono-Support-for-Editor-" + fullVersion + ".pkg";
                                urlIl2CppWin = "https://download.unity3d.com/download_unity/" + versionId + "/TargetSupportInstaller/UnitySetup-Windows-IL2CPP-Support-for-Editor-" + fullVersion + ".exe";
                            }
                        }
                        else
                            break;

                        boolean alreadyHasVersion = false;
                        for (UnityVersion uv : unityVersions) {
                            if (uv.version.equals(foundVersion)) {
                                alreadyHasVersion = true;
                                break;
                            }
                        }

                        if (!alreadyHasVersion)
                            unityVersions.add(new UnityVersion(foundVersion, fullVersion, foundUrl, urlIl2CppWin));
                    }

                    List<UnityVersion> newVersions = new ArrayList<>();

                    for (UnityVersion unityVersion : unityVersions)
                        if (!installedVersions.containsKey(unityVersion.version))
                            newVersions.add(unityVersion);

                    System.out.println("unity3d.com returned " + newVersions.size() + " new versions");

                    if (!newVersions.isEmpty()) {
                        if (newVersions.size() < 10) {
                            initialisingUnityVersions = false;
                            StringBuilder message = new StringBuilder("New Unity version published:");
                            for (UnityVersion newVersion : newVersions)
                                message.append("\n- ").append(newVersion.version);
                            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessage(message.toString()).queue();
                            JDAManager.getJDA().getNewsChannelById(979786573010833418L /* #unity-version-updates */).sendMessage(message.toString()).queue(s -> s.crosspost().queue());
                        }
                        else
                            initialisingUnityVersions = true;
                    }
                    else
                        continue;

                    if (isRunningCheck) {
                        while (isRunningCheck)
                            Thread.sleep(100);
                        JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessage("Waiting for running check to finish").queue();
                    }
                    isRunningCheck = true;

                    loadIcalls();

                    for (UnityVersion newVersion : newVersions) {
                        downloadUnity(newVersion);

                        // run tools sanity checks

                        runHashChecker(newVersion.version);
                        if (!initialisingUnityVersions) {
                            runICallChecker(newVersion.version, null);
                            runMonoStructChecker(newVersion.version);
                        }
                        // VFTables Checker
                    }

                    if (new File(downloadPath).list() == null) {
                        ExceptionUtils.reportException("Unity download path is missing");
                        return;
                    }

                    List<String> allUnityVersions = new ArrayList<>();
                    for (String version : new File(downloadPath).list())
                        if (!version.endsWith("_tmp"))
                            allUnityVersions.add(version);

                    allUnityVersions.sort(new UnityVersionComparator());


                    // ICall init check
                    if (initialisingUnityVersions) {

                        StringBuilder sb = new StringBuilder();
                        for (String version : allUnityVersions) {
                            runICallChecker(version, sb);
                            sb.append(version).append("\n---------------------------------------------------------------------------------------\n");
                        }

                        JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                            Utils.wrapMessageInEmbed("Icall checks results:", Color.gray)
                        ).addFiles(FileUpload.fromData(sb.toString().getBytes(), "icall_init_report.txt")).queue();
                    }

                    // MonoStruct init check
                    boolean originalInitialisingUnityVersions = initialisingUnityVersions;
                    if (!monoStructs.isEmpty() && monoStructs.get(0).rows.isEmpty())
                        initialisingUnityVersions = true;

                    if (initialisingUnityVersions) {
                        for (MonoStructInfo msi : monoStructs)
                            msi.rows.clear();

                        for (String version : allUnityVersions)
                            runMonoStructChecker(version);

                        for (MonoStructInfo msi : monoStructs) {
                            StringBuilder results = new StringBuilder();
                            for (MonoStructRow msr : msi.rows)
                                results.append("\n\n`").append(String.join("`, `", msr.unityVersions)).append("`\n```\n").append(String.join("\n", msr.fields)).append("```");
                            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                                Utils.wrapMessageInEmbed("MonoStruct checks results for " + msi.name + ":" + results, Color.gray)
                            ).queue();
                        }
                    }
                    initialisingUnityVersions = originalInitialisingUnityVersions;

                    // VFTable init check
                    // TODO VFTable init check

                    isRunningCheck = false;
                }
                catch (Exception e) {
                    ExceptionUtils.reportException("Unhandled exception in UnityVersionMonitor", e);
                    isRunningCheck = false;
                }
            }

        }, "UnityVersionMonitor");
        thread.setDaemon(true);
        thread.start();
    }



    public static void runFullICallCheck() {
        if (isRunningCheck) {
            while (isRunningCheck)
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessage("Waiting for running check to finish").queue();
        }
        isRunningCheck = true;
        loadIcalls();

        try {
            List<String> allUnityVersions = new ArrayList<>();
            for (String version : new File(downloadPath).list())
                if (!version.endsWith("_tmp"))
                    allUnityVersions.add(version);

            allUnityVersions.sort(new UnityVersionComparator());

            StringBuilder sb = new StringBuilder();
            for (String version : allUnityVersions) {
                sb.append("\n").append(version).append(" ---------------------------------------------------------------------------------------\n");
                runICallChecker(version, sb);
            }

            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                Utils.wrapMessageInEmbed("Icall checks results:", Color.gray)
            ).addFiles(FileUpload.fromData(sb.toString().getBytes(), "icall_init_report.txt")).queue();
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Unhandled exception in UnityVersionMonitor", e);
        }

        isRunningCheck = false;
    }





    private static void downloadUnity(UnityVersion uv) {
        File targetFile = new File(downloadPath + "/" + uv.version);
        File targetFileTmp = new File(downloadPath + "/" + uv.version + "_tmp");
        if (targetFile.exists()) {
            try (Stream<Path> filesToDelete = Files.walk(targetFile.toPath())) {
                filesToDelete.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            catch (Exception e) {
                ExceptionUtils.reportException("Failed to delete unity folder " + uv.version, e);
                return;
            }
        }

        if (targetFileTmp.exists()) {
            try (Stream<Path> filesToDelete = Files.walk(targetFileTmp.toPath())) {
                filesToDelete.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
            catch (Exception e) {
                ExceptionUtils.reportException("Failed to delete unity temp folder " + uv.version, e);
                return;
            }
        }

        List<String> installedArchitectures = installedVersions.get(uv.version);

        if (installedArchitectures == null || !installedArchitectures.contains("windows mono")) {

            System.out.println("Downloading " + uv.downloadUrl);
            try (
                FileOutputStream fileOutputStream = new FileOutputStream("unityversionsmonitor/unitydownload_" + uv.version + ".dat");
                FileChannel fileChannel = fileOutputStream.getChannel()
            ) {
                ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(uv.downloadUrl).openStream());
                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            }
            catch (Exception e) {
                ExceptionUtils.reportException("Failed to download unity version " + uv.version + " (mono)", e);
                return;
            }

            extractFilesFromArchive(uv, false, false);

            saveInstalledVersionCache(uv.version, "windows mono");

        }

        if ((installedArchitectures == null || !installedArchitectures.contains("windows il2cpp")) && uv.downloadUrlIl2CppWin != null) {

            System.out.println("Downloading " + uv.downloadUrlIl2CppWin);
            try (
                FileOutputStream fileOutputStream = new FileOutputStream("unityversionsmonitor/unitydownload_" + uv.version + ".dat");
                FileChannel fileChannel = fileOutputStream.getChannel()
            ) {
                ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(uv.downloadUrlIl2CppWin).openStream());
                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            }
            catch (Exception e) {
                ExceptionUtils.reportException("Failed to download unity version " + uv.version + " (il2cpp)", e);
                return;
            }

            boolean useNSISBIExtractor = uv.version.startsWith("202") && !uv.version.startsWith("2020.1");
            extractFilesFromArchive(uv, true, useNSISBIExtractor);

            saveInstalledVersionCache(uv.version, "windows il2cpp");
        }

        try (Stream<Path> filesToDelete = Files.walk(targetFileTmp.toPath())) {
            filesToDelete.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to delete unity temp folder " + uv.version, e);
            return;
        }

        new File("unityversionsmonitor/unitydownload_" + uv.version + ".dat").delete();
        new File("unityversionsmonitor/Payload~").delete();
    }

    public static void loadInstalledVersionCache() {
        try {
            System.out.println("Loading versions cache");
            installedVersions.putAll(gson.fromJson(Files.readString(Paths.get("unityversionsmonitor/unityInstallCache.json")), new TypeToken<HashMap<String, ArrayList<String>>>(){}.getType()));
            System.out.println("Done loading versions cache");
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to load unity installation cache", e);
        }
    }

    public static void loadMonoStructCache() {
        try {
            System.out.println("Loading MonoStructs cache");
            Map<String, List<MonoStructRow>> monoStructsSave = gson.fromJson(Files.readString(Paths.get("unityversionsmonitor/monoStructCache.json")), new TypeToken<HashMap<String, List<MonoStructRow>>>(){}.getType());
            for (MonoStructInfo msi : monoStructs) {
                List<MonoStructRow> msr = monoStructsSave.get(msi.name);
                if (msr != null)
                    msi.rows.addAll(msr);
            }
            System.out.println("Done loading MonoStructs cache");
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to load MonoStructs cache", e);
        }
    }

    public static void loadIcalls() {
        try {
            System.out.println("Loading Icalls");
            List<UnityICall> storedIcalls = gson.fromJson(Files.readString(Paths.get("unityversionsmonitor/icalls.jsonc")), new TypeToken<ArrayList<UnityICall>>(){}.getType());
            icalls.clear();
            icalls.addAll(storedIcalls);
            System.out.println("Done loading Icalls");
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to load Icalls", e);
        }
    }

    public static void saveInstalledVersionCache(String unityVersion, String architecture) {
        List<String> installedArchitectures = installedVersions.computeIfAbsent(unityVersion, k -> new ArrayList<>());
        installedArchitectures.add(architecture);

        try {
            Files.write(Paths.get("unityversionsmonitor/unityInstallCache.json"), gson.toJson(installedVersions).getBytes());
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to save unity installation cache", e);
        }
    }

    public static void saveMonoStructCache() {
        try {
            Map<String, List<MonoStructRow>> monoStructsSave = new HashMap<>();
            for (MonoStructInfo msi : monoStructs)
                monoStructsSave.put(msi.name, msi.rows);
            Files.write(Paths.get("unityversionsmonitor/monoStructCache.json"), gson.toJson(monoStructsSave).getBytes());
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to save MonoStructs cache", e);
        }
    }

    public static void extractFilesFromArchive(UnityVersion version, boolean isil2cpp, boolean useNSISExtractor) {
        String internalPath = "Variations";
        String monoManagedSubpath = getMonoManagedSubpath(version.version);

        if (version.version.startsWith("3.")) {
            internalPath = "Data/PlaybackEngines/";
        }
        else if (version.version.startsWith("4.")) {
            if (version.version.startsWith("4.5") ||
                version.version.startsWith("4.6") ||
                version.version.startsWith("4.7")) {
                internalPath = "Data/PlaybackEngines/windowsstandalonesupport/Variations";
            }
            else {
                internalPath = "Data/PlaybackEngines/";
            }
        }
        else if (version.version.startsWith("5.")) {
            if (version.version.startsWith("5.3")) {
                internalPath = "Editor/Data/PlaybackEngines/WebPlayer/";
            }
            else {
                internalPath = "Editor/Data/PlaybackEngines/windowsstandalonesupport/Variations";
            }
        }


        String internalPathZip;
        if (useNSISExtractor) {
            internalPathZip = "\\\\$_OUTDIR/Variations/(.*_il2cpp/UnityPlayer.*(dll|pdb)|" + monoManagedSubpath + "/.*dll)";
        }
        else {
            internalPathZip = version.version.startsWith("20") ? (version.version.startsWith("2017.1") ? "./" : (isil2cpp ? "\\$INSTDIR\\$*/" : "./")) : "";
            internalPathZip += internalPath;
            internalPathZip = "\"" + internalPathZip + (version.version.startsWith("20") && !version.version.startsWith("2017.1") ? "/*/UnityPlayer.dll" : "/*/*.exe") + "\" \"" + internalPathZip + "/*/UnityPlayer*.pdb\" \"" + internalPathZip + "/" + monoManagedSubpath + "/*.dll\"";
        }

        System.out.println("Extracting DLLs from Archive");
        if (!new File(downloadPath).exists())
            new File(downloadPath).mkdir();
        try {
            if (!extractFiles(downloadPath + "/" + version.version + "_tmp", "unityversionsmonitor/unitydownload_" + version.version + ".dat", internalPathZip, !isil2cpp && version.version.startsWith("20"), useNSISExtractor, true)) {
                ExceptionUtils.reportException("Failed to extract Unity version " + version.version + " (" + (isil2cpp ? "il2cpp" : "mono") + ")");
                return;
            }
        }
        catch (Exception e) {
            ExceptionUtils.reportException("Failed to extract Unity version " + version.version + " (" + (isil2cpp ? "il2cpp" : "mono") + ")", e);
            return;
        }
        String tomoveFolder = downloadPath + "/" + version.version + "_tmp";
        if (isil2cpp)
            tomoveFolder = new File(tomoveFolder).listFiles(File::isDirectory)[0].getPath();
        tomoveFolder += "/" + internalPath;
        moveDirectory(new File(tomoveFolder), new File(downloadPath + "/" + version.version));
    }

    private static boolean extractFiles(String outputPath, String zipPath, String internalPath, boolean isPkg, boolean useNSISBIExtractor, boolean keepFilePath) throws IOException, InterruptedException {
        if (useNSISBIExtractor)
            return runProgram("UnityNSISReader", "sh", "-c", "mono unityversionsmonitor/UnityNSISReader.exe \"-f" + zipPath + "\" \"-o" + outputPath + "\" \"-r" + internalPath + "\"") == 0;

        if (isPkg) {
            if (runProgram("7z", "sh", "-c", "7z " + (keepFilePath ? "x" : "e") + " \"" + zipPath + "\" -ounityversionsmonitor \"Payload~\" -y") != 0)
                return false;

            return runProgram("7z", "sh", "-c", "7z " + (keepFilePath ? "x" : "e") + " \"unityversionsmonitor/Payload~\" -o\"" + outputPath + "\" " + internalPath + " -y") == 0;
        }

        return runProgram("7z", "sh", "-c", "7z " + (keepFilePath ? "x" : "e") + " \"" + zipPath + "\" -o\"" + outputPath + "\" " + internalPath + " -y") == 0;
    }

    private static int runProgram(String name, String... command) throws IOException, InterruptedException {
        StringBuilder printCmd = new StringBuilder();
        for (String param : command)
            printCmd.append("\"").append(param.replace("\"", "\\\"")).append("\" ");
        System.out.println("Running command: " + printCmd);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null)
                System.out.println("[" + name + "] " + line);
        }

        return p.waitFor();
    }

    private static void moveDirectory(File src, File dest) {
        if (!dest.exists())
            dest.mkdirs();
        String targetDirPath = dest.getAbsolutePath();
        File[] files = src.listFiles();
        for (File file : files)
            file.renameTo(new File(targetDirPath + File.separator + file.getName()));
    }

    private static void runHashChecker(String unityVersion)  throws IOException, InterruptedException {
        Map<String, Map<String, Integer>> results = null;

        System.out.println("Running command: \"sh\" \"-c\" \"mono unityversionsmonitor/HashChecker.exe --uv=" + unityVersion + " --nhro\"");
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", "mono unityversionsmonitor/HashChecker.exe --uv=" + unityVersion + " --nhro");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[HashChecker] " + line);
                if (line.startsWith("RESULT_")) {
                    String[] resultParts = line.substring("RESULT_".length()).split(" ", 2);
                    results = gson.fromJson(resultParts[1], new TypeToken<HashMap<String, HashMap<String, Integer>>>(){}.getType());
                }
            }
        }

        p.waitFor();

        if (results == null) {
            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                Utils.wrapMessageInEmbed("HashChecker has reported no result for Unity " + unityVersion, Color.red)
            ).queue();
            return;
        }

        StringBuilder reports = new StringBuilder();
        for (Entry<String, Map<String, Integer>> arch : results.entrySet()) {
            for (Entry<String, Integer> hash : arch.getValue().entrySet()) {
                if (hash.getValue() > 1)
                    reports.append(arch.getKey()).append(" - ").append(hash.getKey()).append(": ").append(hash.getValue()).append(" results\n");
                else if (hash.getValue() == 0)
                    reports.append(arch.getKey()).append(" - ").append(hash.getKey()).append(": Hash not valid\n");
                else if (hash.getValue() == -1)
                    reports.append(arch.getKey()).append(" - ").append(hash.getKey()).append(": No hash for this version\n");
                else if (hash.getValue() == -2)
                    reports.append(arch.getKey()).append(" - ").append(hash.getKey()).append(": File not found\n");
            }
        }

        if (!reports.isEmpty()) {
            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                Utils.wrapMessageInEmbed("Failed to validate all hashes for Unity " + unityVersion + ":\n\n" + reports, Color.red)
            ).queue();
        }
        else {
            if (!initialisingUnityVersions) {
                JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                    Utils.wrapMessageInEmbed("Hash check succeeded for Unity " + unityVersion, Color.green)
                ).queue();
            }
        }
    }

    public static void runICallChecker(String unityVersion, StringBuilder stringBuilder) {

        System.out.println("[" + unityVersion + "] Running icall check for Unity " + unityVersion);

        StringBuilder reportNoValidVersion = new StringBuilder();

        Map<String, List<UnityICall>> assemblies = new HashMap<>();
        for (UnityICall icall : icalls) {
            if (!isUnityVersionOverOrEqual(unityVersion, icall.unityVersions)) {
                boolean found = false;
                for (UnityICall oldICallEntry : icall.oldICalls) {
                    if (isUnityVersionOverOrEqual(unityVersion, oldICallEntry.unityVersions)) {
                        System.out.println("[" + unityVersion + "] Icall " + icall.icall + " => " + oldICallEntry.icall);
                        icall = oldICallEntry;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println("[" + unityVersion + "] ICall has no valid version: " + icall.icall);
                    reportNoValidVersion.append(icall.icall);
                    continue;
                }
            }

            List<UnityICall> icallsForAssembly = assemblies.computeIfAbsent(icall.assemblyName, k -> new ArrayList<>());
            icallsForAssembly.add(icall);
        }

        StringBuilder reportNoType = new StringBuilder();
        StringBuilder reportNoMethod = new StringBuilder();
        StringBuilder reportMismatchingParams = new StringBuilder();

        for (Entry<String, List<UnityICall>> assemblyEntry : assemblies.entrySet()) {
            String assemblyName = assemblyEntry.getKey();
            AssemblyDefinition ad = AssemblyDefinition.readAssembly(downloadPath + "/" + unityVersion + "/" + getMonoManagedSubpath(unityVersion) + "/" + assemblyName + ".dll", new ReaderParameters(ReadingMode.Deferred, new CecilAssemblyResolverProvider.AssemblyResolver()));
            ModuleDefinition mainModule = ad.getMainModule();

            for (UnityICall icall : assemblyEntry.getValue()) {
                String[] icallParts = icall.icall.split("::", 2);
                TypeDefinition typeDefinition = mainModule.getType(icallParts[0]);
                if (typeDefinition == null) {
                    reportNoType.append("\n").append(icall.icall);
                    continue;
                }

                boolean found = false;
                boolean foundAndMatches = false;
                StringBuilder similarMethods = new StringBuilder();
                for (MethodDefinition md : typeDefinition.getMethods()) {
                    if (md.getName().equals(icallParts[1])) {
                        found = true;
                        boolean valid = true;
                        List<ParameterDefinition> parameterDefs = md.getParameters();

                        String returnTypeTranslated = md.getReturnType().getFullName();
                        if (md.getReturnType().isArray())
                            returnTypeTranslated += "[]";

                        List<String> parameterDefsTranslated = parameterDefs.stream()
                            .map(pd -> {
                                String fullname = pd.getParameterType().getFullName();
                                if (fullname.endsWith("&"))
                                    fullname = "ref " + fullname.substring(0, fullname.length() - 1);
                                if (pd.getParameterType().isArray())
                                    fullname += "[]";

                                return fullname;
                            })
                            .collect(Collectors.toList());
                        if (!md.isStatic())
                            parameterDefsTranslated.add(0, icallParts[0]);

                        if (!returnTypeTranslated.equals(icall.returnType) || parameterDefsTranslated.size() != icall.parameters.length) {
                            valid = false;
                        }
                        else {
                            for (int i = 0; i < icall.parameters.length; ++i) {
                                if (!parameterDefsTranslated.get(i).equals(icall.parameters[i])) {
                                    valid = false;
                                    break;
                                }
                            }
                        }

                        if (valid) {
                            foundAndMatches = true;
                            break;
                        }
                        else
                            similarMethods.append("\n`").append(returnTypeTranslated).append(" <- ").append(String.join(", ", parameterDefsTranslated)).append("`");
                        // ELSE it's valid
                    }
                }

                if (!found) {
                    reportNoMethod.append("\n").append(icall.icall);
                    System.out.println("[" + unityVersion + "] ICall method not found: " + icall.icall);
                }
                else if (!foundAndMatches) {
                    reportMismatchingParams.append("\n\n").append(icall.icall);
                    reportMismatchingParams.append("\nExpected:\n`").append(icall.returnType).append(" <- ").append(String.join(", ", icall.parameters)).append("`");
                    reportMismatchingParams.append("\nFound:").append(similarMethods);
                    System.out.println("[" + unityVersion + "] ICall parameters mismatches for " + icall.icall);
                    System.out.println("[" + unityVersion + "] Expected: " + icall.returnType + " <- " + String.join(", ", icall.parameters));
                    System.out.println("[" + unityVersion + "] Found: " + similarMethods);
                }
            }

            ad.dispose();
        }
        // 3. Send result

        if (!initialisingUnityVersions) {
            boolean hasError = false;
            if (!reportNoValidVersion.isEmpty()) {
                hasError = true;
                if (stringBuilder != null)
                    stringBuilder.append("**The following icalls have no definition for Unity ").append(unityVersion).append(":**").append(reportNoMethod);
                else
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                        Utils.wrapMessageInEmbed("**The following icalls have no definition for Unity " + unityVersion + ":**" + reportNoMethod, Color.red)
                    ).queue();
            }
            if (!reportNoType.isEmpty()) {
                hasError = true;
                if (stringBuilder != null)
                    stringBuilder.append("**Failed to find the following icall managed types for Unity ").append(unityVersion).append(":**").append(reportNoType);
                else
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                        Utils.wrapMessageInEmbed("**Failed to find the following icall managed types for Unity " + unityVersion + ":**" + reportNoType, Color.red)
                    ).queue();
            }
            if (!reportNoMethod.isEmpty()) {
                hasError = true;
                if (stringBuilder != null)
                    stringBuilder.append("**Failed to find the following icall managed methods for Unity ").append(unityVersion).append(":**").append(reportNoMethod);
                else
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                        Utils.wrapMessageInEmbed("**Failed to find the following icall managed methods for Unity " + unityVersion + ":**" + reportNoMethod, Color.red)
                    ).queue();
            }
            if (!reportMismatchingParams.isEmpty()) {
                hasError = true;
                if (stringBuilder != null)
                    stringBuilder.append("**The following icall methods mismatch for Unity ").append(unityVersion).append(":**").append(reportMismatchingParams);
                else
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                    Utils.wrapMessageInEmbed("**The following icall methods mismatch for Unity " + unityVersion + ":**" + reportMismatchingParams, Color.red)
                ).queue();
            }

            if (!hasError)
                if (stringBuilder != null)
                    stringBuilder.append("ICall check succeeded for Unity ").append(unityVersion);
                else
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                        Utils.wrapMessageInEmbed("ICall check succeeded for Unity " + unityVersion, Color.green)
                    ).queue();
        }
    }

    public static void runMonoStructChecker(String unityVersion) {

        int successfullChecks = 0;

        for (MonoStructInfo msi : monoStructs) {

            // 1. Fetch struct

            AssemblyDefinition ad = AssemblyDefinition.readAssembly(downloadPath + "/" + unityVersion + "/" + getMonoManagedSubpath(unityVersion) + "/" + msi.assembly + ".dll", new ReaderParameters(ReadingMode.Deferred, new CecilAssemblyResolverProvider.AssemblyResolver()));
            ModuleDefinition mainModule = ad.getMainModule();

            TypeDefinition typeDefinition = mainModule.getType(msi.name);
            if (typeDefinition == null) {
                for (MonoStructInfo alt : msi.altStructs) {
                    typeDefinition = mainModule.getType(alt.name);
                    if (typeDefinition != null)
                        break;
                }
                if (typeDefinition == null) {
                    System.out.println("Failed to validate the following MonoStruct for Unity " + unityVersion + ": " + msi.name);
                    continue;
                }
            }

            List<String> fields = new ArrayList<>();

            if (typeDefinition.isEnum()) {
                int fieldOffset = 0;
                for (FieldDefinition fieldDef : typeDefinition.getFields()) {
                    if (fieldDef.getName().equals("value__"))
                        continue;

                    int fieldConstant = (int) fieldDef.getConstant();
                    if (fieldConstant != fieldOffset)
                        fields.add(fieldDef.getName() + " = " + (fieldOffset = fieldConstant));
                    else
                        fields.add(fieldDef.getName());

                    fieldOffset++;
                }
            }
            else
                for (FieldDefinition fieldDef : typeDefinition.getFields())
                    if (!fieldDef.isStatic())
                        fields.add(fieldDef.getFieldType().getFullName() + " " + fieldDef.getName());

            ad.dispose();


            // 2. Compare

            // Foreach MSI, GET the 1st one WHERE compareUnityVersions(unityVersion, msi.version) > 0 OR NULL
            MonoStructRow msrTarget = null;
            int msrTargetIndex = 0;
            for (int iMSR = 0; iMSR < msi.rows.size(); ++iMSR) {
                MonoStructRow msr = msi.rows.get(iMSR);
                for (String uv : msr.unityVersions) {
                    if (compareUnityVersions(unityVersion, uv) > 0) {
                        msrTarget = msr;
                        msrTargetIndex = iMSR;
                        break;
                    }
                }
                if (msrTarget != null)
                    break;
            }
            // If found, check if struct matches -> fieldsMatch = true
            boolean fieldsMatch = msrTarget != null && monoStructContainsFields(msrTarget, fields);
            // If 'fieldsMatch'
            if (fieldsMatch) {
                // If for all versions of row, !isUnityVersionOverOrEqual(unityVersion, matchedVersion) THEN add version string Else OK
                boolean isVersionValid = isUnityVersionOverOrEqual(unityVersion, msrTarget.unityVersions.toArray(new String[0]));

                if (isVersionValid)
                    ++successfullChecks;
                else {
                    String oldUnityVersions = String.join("`, `", msrTarget.unityVersions);
                    msrTarget.unityVersions.add(unityVersion);
                    if (!initialisingUnityVersions)
                        JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                            Utils.wrapMessageInEmbed("New Minimal Unity versions for MonoStruct " + msi.name + ":\n**OLD:** `" + oldUnityVersions + "`\n**NEW:** `" + String.join("`, `", msrTarget.unityVersions) + "`", Color.red)
                        ).queue();
                }
            }
            // Else
            else if (msrTarget != null) {
                if (msrTargetIndex > 0)
                    msrTarget = msi.rows.get(--msrTargetIndex);
                if (msrTarget.fields.size() == fields.size()) {
                    fieldsMatch = true;
                    for (int iField = 0; iField < msrTarget.fields.size(); ++iField) {
                        if (!msrTarget.fields.get(iField).equals(fields.get(iField))) {
                            fieldsMatch = false;
                            break;
                        }
                    }
                }

                if (fieldsMatch) {
                    String oldUnityVersions = String.join("`, `", msrTarget.unityVersions);
                    msrTarget.unityVersions.add(unityVersion);
                    if (!initialisingUnityVersions)
                        JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                            Utils.wrapMessageInEmbed("New Minimal Unity versions for MonoStruct " + msi.name + ":\n**OLD:** `" + oldUnityVersions + "`\n**NEW:** `" + String.join("`, `", msrTarget.unityVersions) + "`", Color.red)
                        ).queue();
                }
                else {
                    msi.rows.add(msrTargetIndex, new MonoStructRow(unityVersion, fields));

                    StringBuilder report = new StringBuilder(msi.name + "\n```\n");
                    for (String field : fields)
                        report.append(field).append("\n");
                    report.append("```");
                    if (!initialisingUnityVersions)
                        JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                            Utils.wrapMessageInEmbed("New MonoStructs " + msi.name + " for Unity " + unityVersion + ":\n\n" + report, Color.red)
                        ).queue();
                }
            }
            else {
                // Add new row on the beginning
                msi.rows.add(0, new MonoStructRow(unityVersion, fields));

                StringBuilder report = new StringBuilder(msi.name + "\n```\n");
                for (String field : fields)
                    report.append(field).append("\n");
                report.append("```");
                if (!initialisingUnityVersions)
                    JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                        Utils.wrapMessageInEmbed("New MonoStructs " + msi.name + " for Unity " + unityVersion + ":\n\n" + report, Color.red)
                    ).queue();
            }
        }

        if (!initialisingUnityVersions && successfullChecks == monoStructs.size()) {
            JDAManager.getJDA().getTextChannelById(876466104036393060L /* #lum-status */).sendMessageEmbeds(
                Utils.wrapMessageInEmbed("MonoStruct checks succeeded for Unity " + unityVersion, Color.green)
            ).queue();
        }

        saveMonoStructCache();
    }

    private static String getMonoManagedSubpath(String version) {
        String monoManagedSubpath = "win64_player_nondevelopment_mono/Data";

        if (version.startsWith("3.")) {
            monoManagedSubpath = "windows64standaloneplayer";
        }
        else if (version.startsWith("4.")) {
            if (version.startsWith("4.5") ||
                version.startsWith("4.6") ||
                version.startsWith("4.7")) {
                monoManagedSubpath = "win64_nondevelopment/Data";
            }
            else {
                monoManagedSubpath = "windows64standaloneplayer";
            }
        }
        else if (version.startsWith("5.")) {
            if (version.startsWith("5.3")) {
                monoManagedSubpath = "win64_nondevelopment_mono/Data";
            }
            else {
                monoManagedSubpath = "win64_nondevelopment/Data";
            }
        }
        else if (version.startsWith("20")) {
            if (compareUnityVersions(version, "2021.2.0") < 0)
                monoManagedSubpath = "win64_nondevelopment_mono/Data";
        }

        return monoManagedSubpath + "/Managed";
    }

    public static int compareUnityVersions(String left, String right) {
        int[] leftparts = getUnityVersionNumbers(left);
        int[] rightparts = getUnityVersionNumbers(right);

        long leftsum = leftparts[0] * 10000L + leftparts[1] * 100L + leftparts[2];
        long rightsum = rightparts[0] * 10000L + rightparts[1] * 100L + rightparts[2];

        return Long.compare(leftsum, rightsum);
    }

    private static int[] getUnityVersionNumbers(String s) {
        String[] numbersS = s.split("\\.");
        int[] numbers = new int[numbersS.length];
        for (int i = 0; i < numbersS.length; ++i)
            numbers[i] = Integer.parseInt(numbersS[i]);

        return numbers;
    }

    /*
    public static boolean isUnityVersionOverOrEqual(String currentversion, String validversion)
    {
        String[] versionparts = currentversion.split("\\.");

        String[] validversionparts = validversion.split("\\.");

        if (
            Integer.parseInt(versionparts[0]) >= Integer.parseInt(validversionparts[0]) &&
            Integer.parseInt(versionparts[1]) >= Integer.parseInt(validversionparts[1]) &&
            Integer.parseInt(versionparts[2]) >= Integer.parseInt(validversionparts[2]))
            return true;

        return false;
    }
    */

    public static boolean isUnityVersionOverOrEqual(String currentversion, String[] validversions) {
        if (validversions == null || validversions.length == 0)
            return true;

        String[] versionparts = currentversion.split("\\.");

        for (String validversion : validversions) {

            String[] validversionparts = validversion.split("\\.");

            if (
                Integer.parseInt(versionparts[0]) >= Integer.parseInt(validversionparts[0]) &&
                Integer.parseInt(versionparts[1]) >= Integer.parseInt(validversionparts[1]) &&
                Integer.parseInt(versionparts[2]) >= Integer.parseInt(validversionparts[2]))
                return true;

        }

        return false;
    }

    private static boolean monoStructContainsFields(MonoStructRow msi, List<String> fields) {
        if (msi.fields.size() == fields.size()) {
            for (int i = 0; i < fields.size(); ++i)
                if (!msi.fields.get(i).equals(fields.get(i)))
                    return false;

            return true;
        }
        return false;
    }

    private record UnityVersion(String version, String fullVersion, String downloadUrl, String downloadUrlIl2CppWin) {
    }

    private static class UnityICall {
        public final String icall;
        public final byte[] icallUtf8;
        public final String[] unityVersions;
        public final String assemblyName;
        public final String returnType;
        public final String[] parameters;
        public final List<UnityICall> oldICalls = new ArrayList<>(0);

        public UnityICall(String icall, String[] unityVersions, String assemblyName, String returnType, String[] parameters, UnityICall... oldICalls) {
            this.icall = icall;
            this.icallUtf8 = icall.getBytes(StandardCharsets.UTF_8);
            this.unityVersions = unityVersions;
            this.assemblyName = assemblyName;
            this.returnType = returnType;
            this.parameters = parameters;
            if (oldICalls != null)
                Collections.addAll(this.oldICalls, oldICalls);
        }
    }

    private static class MonoStructInfo {
        public final String name;
        public final String assembly;
        public final List<MonoStructRow> rows = new ArrayList<>();
        public final MonoStructInfo[] altStructs;

        public MonoStructInfo(String fullname, String assembly, MonoStructInfo... altStructs) {
            this.name = fullname;
            this.assembly = assembly;
            this.altStructs = altStructs != null ? altStructs : new MonoStructInfo[0];
        }
    }

    private static class MonoStructRow {
        public final List<String> unityVersions;
        public final List<String> fields;

        public MonoStructRow(String unityVersion, List<String> fields) {
            unityVersions = new ArrayList<>(1);
            unityVersions.add(unityVersion);
            this.fields = fields;
        }
    }

    private static class UnityVersionComparator implements Comparator<String> {
        @Override
        public int compare(String left, String right) {
            return compareUnityVersions(left, right);
        }
    }

}
