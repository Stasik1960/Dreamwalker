import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Rebuilds ALT sprite overrides from immutable inputs, never from generated outputs. */
public final class MaterializeV12Palettes {
    record Palette(int[][] channels) {
        int apply(int argb) {
            return (argb & 0xff000000) | (channels[0][(argb >>> 16) & 255] << 16)
                | (channels[1][(argb >>> 8) & 255] << 8) | channels[2][argb & 255];
        }
    }
    record Region(int x0, int y0, int x1, int y1, Palette palette) {
        boolean contains(int x, int y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
    }
    static Palette required(Map<String, Palette> palettes, String name) {
        Palette p = palettes.get(name);
        if (p == null) throw new IllegalArgumentException("Unknown palette: " + name);
        return p;
    }
    static byte[] read(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) { return in.readAllBytes(); }
    }
    static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static Path under(Path root, String relative) {
        Path result = root.resolve(relative).normalize();
        if (!result.startsWith(root)) throw new IllegalArgumentException("Path escapes resources: " + relative);
        return result;
    }
    record Tone(boolean luminance, double[] scale, double[] offset) {
        int apply(int argb) {
            int r=(argb >>> 16)&255, g=(argb >>> 8)&255, b=argb&255;
            int l=(77*r+150*g+29*b+128)>>>8;
            int[] values = luminance ? new int[]{l,l,l} : new int[]{r,g,b};
            int result = argb & 0xff000000;
            for (int c=0;c<3;c++) result |= Math.max(0,Math.min(255,(int)Math.round(values[c]*scale[c]+offset[c]))) << (16-8*c);
            return result;
        }
    }
    record FamilyRegion(int x0,int y0,int x1,int y1,Tone tone) {
        boolean contains(int x,int y) { return x>=x0 && x<x1 && y>=y0 && y<y1; }
    }
    static Tone tone(JsonObject object) {
        if (object == null) return null;
        JsonArray s=object.getAsJsonArray("scale"), o=object.getAsJsonArray("offset");
        if(s.size()!=3 || o.size()!=3) throw new IllegalArgumentException("Invalid family channels");
        double[] scale=new double[3], offset=new double[3];
        for(int i=0;i<3;i++) {
            scale[i]=s.get(i).getAsDouble(); offset[i]=o.get(i).getAsDouble();
            if(!Double.isFinite(scale[i]) || !Double.isFinite(offset[i]) || scale[i]<0 || scale[i]>2 || Math.abs(offset[i])>64)
                throw new IllegalArgumentException("Invalid family tone range");
        }
        return new Tone(object.has("luminance") && object.get("luminance").getAsBoolean(),scale,offset);
    }
    static String spritePath(String sprite) {
        if(!sprite.matches("(?:bloodborne_dw|bloodborne_rp):[a-z0-9_./-]+") || sprite.contains("..")) throw new IllegalArgumentException("Invalid sprite: "+sprite);
        return "assets/"+sprite.replace(":","/textures/")+".png";
    }
    static void bakeFamily(Map<String,String> options, Path root, Set<String> v12, JsonArray receipt) throws Exception {
        JsonObject data;
        try(Reader reader=Files.newBufferedReader(Path.of(options.get("--family")))) { data=JsonParser.parseReader(reader).getAsJsonObject(); }
        if(data.get("schema").getAsInt()!=1) throw new IllegalArgumentException("Invalid family schema");
        Set<String> seen=new HashSet<>();
        try(ZipFile base=new ZipFile(options.get("--base")); ZipFile inputs=new ZipFile(options.get("--inputs"))) {
            for(JsonElement raw:data.getAsJsonArray("sprites")) {
                JsonObject rule=raw.getAsJsonObject();
                String sprite=rule.get("sprite").getAsString(), sourceSprite=rule.get("sourceSprite").getAsString();
                String targetPath=spritePath(sprite), sourcePath=spritePath(sourceSprite);
                if(!seen.add(sprite) || v12.contains(sprite)) throw new IllegalArgumentException("Duplicate or V12-overlapping family sprite: "+sprite);
                for(String profile:List.of("original","alt")) {
                    Tone fallback=tone(rule.getAsJsonObject(profile));
                    List<FamilyRegion> regions=new ArrayList<>(); boolean active=fallback!=null;
                    if(rule.has("regions")) for(JsonElement item:rule.getAsJsonArray("regions")) {
                        JsonObject r=item.getAsJsonObject(); JsonArray b=r.getAsJsonArray("pixels");
                        if(b.size()!=4) throw new IllegalArgumentException("Invalid family rectangle");
                        int x0=b.get(0).getAsInt(),y0=b.get(1).getAsInt(),x1=b.get(2).getAsInt(),y1=b.get(3).getAsInt();
                        if(x0<0||y0<0||x1<=x0||y1<=y0) throw new IllegalArgumentException("Invalid family rectangle bounds");
                        Tone t=tone(r.getAsJsonObject(profile)); active|=t!=null; regions.add(new FamilyRegion(x0,y0,x1,y1,t));
                    }
                    if(!active) continue;
                    byte[] source=read(inputs,profile+"/"+sourcePath); boolean canonical=source!=null;
                    if(source==null) source=read(base,sourcePath);
                    if(source==null) throw new IllegalArgumentException("Missing family source: "+profile+"/"+sourcePath);
                    BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(source));
                    if(decoded==null) throw new IllegalArgumentException("Invalid family PNG: "+sourcePath);
                    int width=decoded.getWidth(),height=decoded.getHeight();
                    for(FamilyRegion r:regions) if(r.x1()>width||r.y1()>height) throw new IllegalArgumentException("Family rectangle outside image: "+sprite);
                    int[] original=decoded.getRGB(0,0,width,height,null,0,width);
                    BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0,0,width,height,original,0,width);
                    for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                        int pixel=original[y*width+x]; if((pixel>>>24)==0) continue;
                        Tone selected=fallback;
                        for(FamilyRegion r:regions) if(r.contains(x,y)) {selected=r.tone();break;}
                        if(selected!=null) image.setRGB(x,y,selected.apply(pixel));
                    }
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    if(!ImageIO.write(image,"PNG",bytes)) throw new IllegalStateException("No PNG writer");
                    byte[] result=bytes.toByteArray(); BufferedImage verified=ImageIO.read(new ByteArrayInputStream(result));
                    if(verified==null||verified.getWidth()!=width||verified.getHeight()!=height) throw new IllegalStateException("Family dimensions changed: "+sprite);
                    int changed=0,alphaChanged=0,outsideChanged=0;
                    for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                        int before=original[y*width+x],after=verified.getRGB(x,y);
                        if(before!=after) {
                            changed++; if((before>>>24)!=(after>>>24)) alphaChanged++;
                            if(fallback==null) {boolean matched=false;for(FamilyRegion r:regions) if(r.contains(x,y)){matched=true;break;}if(!matched)outsideChanged++;}
                        }
                    }
                    if(alphaChanged!=0||outsideChanged!=0) throw new IllegalStateException("Family protected pixels changed: "+sprite);
                    Path output=under(root,"dw_city_visuals/"+profile+"/"+targetPath);
                    Files.createDirectories(output.getParent()); Files.write(output,result);
                    JsonObject entry=new JsonObject(); entry.addProperty("kind","family"); entry.addProperty("profile",profile); entry.addProperty("sprite",sprite);
                    entry.addProperty("sourceSprite",sourceSprite); entry.addProperty("source",canonical?"canonical":"base");
                    entry.addProperty("sourceSha256",sha(source)); entry.addProperty("outputSha256",sha(result)); entry.addProperty("width",width);entry.addProperty("height",height);
                    entry.addProperty("changedPixels",changed);entry.addProperty("alphaChanged",alphaChanged);entry.addProperty("outsideMaskedRegionChanged",outsideChanged);receipt.add(entry);
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 10 && args.length != 12) throw new IllegalArgumentException("Expected --base jar --resources dir --rules json --inputs zip --receipt json");
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!Set.of("--base", "--resources", "--rules", "--inputs", "--receipt", "--family").contains(args[i]) || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Invalid option: " + args[i]);
        }
        Path root = Path.of(options.get("--resources")).toAbsolutePath().normalize();
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(Path.of(options.get("--rules")))) { json = JsonParser.parseReader(reader).getAsJsonObject(); }
        if (json.get("schema").getAsInt() != 1) throw new IllegalArgumentException("Unsupported schema");
        Map<String, Palette> palettes = new HashMap<>();
        for (var entry : json.getAsJsonObject("palettes").entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            JsonArray scale = value.getAsJsonArray("scale"), offset = value.getAsJsonArray("offset");
            if (scale.size() != 3 || offset.size() != 3) throw new IllegalArgumentException("Invalid palette channels");
            int[][] channels = new int[3][256];
            for (int c = 0; c < 3; c++) {
                double s = scale.get(c).getAsDouble(), o = offset.get(c).getAsDouble();
                if (!Double.isFinite(s) || !Double.isFinite(o) || s < 0 || s > 2 || Math.abs(o) > 64) throw new IllegalArgumentException("Invalid palette range");
                for (int n = 0; n < 256; n++) channels[c][n] = Math.max(0, Math.min(255, (int)Math.round(n * s + o)));
            }
            palettes.put(entry.getKey(), new Palette(channels));
        }
        Set<String> seen = new HashSet<>();
        JsonArray receipt = new JsonArray();
        try (ZipFile base = new ZipFile(options.get("--base")); ZipFile inputs = new ZipFile(options.get("--inputs"))) {
            for (JsonElement raw : json.getAsJsonArray("sprites")) {
                JsonObject rule = raw.getAsJsonObject();
                String sprite = rule.get("sprite").getAsString();
                if (!sprite.matches("(?:bloodborne_dw|bloodborne_rp):[a-z0-9_./-]+") || sprite.contains("..") || !seen.add(sprite)) throw new IllegalArgumentException("Invalid or duplicate sprite: " + sprite);
                String path = "assets/" + sprite.replace(":", "/textures/") + ".png";
                Path output = under(root, "dw_city_visuals/alt/" + path);
                Palette fallback = rule.has("palette") ? required(palettes, rule.get("palette").getAsString()) : null;
                List<Region> regions = new ArrayList<>();
                if (rule.has("regions")) for (JsonElement element : rule.getAsJsonArray("regions")) {
                    JsonObject region = element.getAsJsonObject(); JsonArray b = region.getAsJsonArray("pixels");
                    if (b.size() != 4) throw new IllegalArgumentException("Invalid rectangle");
                    int x0=b.get(0).getAsInt(), y0=b.get(1).getAsInt(), x1=b.get(2).getAsInt(), y1=b.get(3).getAsInt();
                    if (x0 < 0 || y0 < 0 || x1 <= x0 || y1 <= y0) throw new IllegalArgumentException("Invalid rectangle bounds");
                    regions.add(new Region(x0,y0,x1,y1,required(palettes,region.get("palette").getAsString())));
                }
                if (fallback == null && regions.isEmpty()) throw new IllegalArgumentException("Empty rule: " + sprite);
                byte[] source = read(inputs, path); boolean canonical = source != null;
                if (source == null) source = read(base, path);
                if (source == null) throw new IllegalArgumentException("Missing sprite: " + path);
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source));
                if (decoded == null) throw new IllegalArgumentException("Invalid PNG: " + path);
                int width = decoded.getWidth(), height = decoded.getHeight();
                for (Region region : regions) if (region.x1() > width || region.y1() > height)
                    throw new IllegalArgumentException("Rectangle outside image: " + sprite);
                int[] original = decoded.getRGB(0, 0, width, height, null, 0, width);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, width, height, original, 0, width);
                for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++) {
                    int before = image.getRGB(x,y); if ((before >>> 24) == 0) continue;
                    Palette selected = fallback;
                    for (Region region : regions) if (region.contains(x,y)) { selected = region.palette(); break; }
                    int after = selected == null ? before : selected.apply(before);
                    if (before != after) image.setRGB(x,y,after);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "PNG", bytes)) throw new IllegalStateException("No PNG writer");
                byte[] result = bytes.toByteArray();
                BufferedImage verified = ImageIO.read(new ByteArrayInputStream(result));
                if (verified == null || verified.getWidth() != width || verified.getHeight() != height)
                    throw new IllegalStateException("Encoded image dimensions changed: " + sprite);
                int changed = 0, alphaChanged = 0, outsideChanged = 0;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int before = original[y * width + x], after = verified.getRGB(x, y);
                    if (before != after) {
                        changed++;
                        if ((before >>> 24) != (after >>> 24)) alphaChanged++;
                        if (fallback == null) {
                            boolean matched = false;
                            for (Region region : regions) if (region.contains(x, y)) { matched = true; break; }
                            if (!matched) outsideChanged++;
                        }
                    }
                }
                if (alphaChanged != 0 || outsideChanged != 0)
                    throw new IllegalStateException("Encoded image changed protected pixels: " + sprite);
                Files.createDirectories(output.getParent()); Files.write(output,result);
                JsonObject item = new JsonObject(); item.addProperty("sprite",sprite); item.addProperty("source",canonical ? "canonical" : "base");
                item.addProperty("sourceSha256",sha(source)); item.addProperty("outputSha256",sha(result)); item.addProperty("width",image.getWidth()); item.addProperty("height",image.getHeight());
                item.addProperty("changedPixels",changed); item.addProperty("alphaChanged",alphaChanged); item.addProperty("outsideMaskedRegionChanged",outsideChanged); receipt.add(item);
            }
        }
        if (options.containsKey("--family")) bakeFamily(options, root, seen, receipt);
        JsonObject report = new JsonObject(); report.addProperty("schema",1); report.add("images",receipt);
        Path receiptPath = Path.of(options.get("--receipt")).toAbsolutePath().normalize();
        if (receiptPath.getParent() != null) Files.createDirectories(receiptPath.getParent());
        Files.writeString(receiptPath, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
