import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.gif.GifControlDirectory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.*;
import java.util.List;

public class launch {
    static BufferedImage underlay;
    static HashMap<String, HashMap<Integer, Integer>> pokemonFrames = new HashMap<String, HashMap<Integer, Integer>>();
    static int frameTime;

    public static void main(String[] args) throws IOException {
        File directoryGIF = new File("assets/pokemons");
        File[] pokemonGIFS = directoryGIF.listFiles();

        if (pokemonGIFS != null) {
            for (File pokemonGIF : pokemonGIFS) {
                if (pokemonGIF.getName().endsWith(".gif")) {
                    createSprite(pokemonGIF);
                }
                System.out.println(pokemonGIF.getName()+ " FrameTime:" +frameTime);
            }
        }
        System.out.println(getPokemonFrames());

        Writer writer = new FileWriter("assets/output/frames.csv");
        for (Map.Entry<String, HashMap<Integer, Integer>> entry : getPokemonFrames().entrySet()) {
            for(Map.Entry<Integer,Integer> dataEntry : entry.getValue().entrySet())
            writer.append(entry.getKey())
                    .append(',')
                    .append(String.valueOf(dataEntry.getKey()))
                    .append(',')
                    .append(String.valueOf(dataEntry.getValue()))
                    .append("\r\n");
        }
        writer.flush();
        writer.close();
    }

    private static int getGifAnimatedTimeLength(InputStream imagePath) {
        int timeLength = 0;
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath);
            List<GifControlDirectory> gifControlDirectories =
                    (List<GifControlDirectory>) metadata.getDirectoriesOfType(GifControlDirectory.class);
            if (gifControlDirectories.size() == 1) { // Do not read delay of static GIF files with single frame.
            } else if (gifControlDirectories.size() >= 1) {
                for (GifControlDirectory gifControlDirectory : gifControlDirectories) {
                    try {
                        if (gifControlDirectory.hasTagName(GifControlDirectory.TAG_DELAY)) {
                            timeLength += gifControlDirectory.getInt(GifControlDirectory.TAG_DELAY);
                        }
                    } catch (MetadataException e) {
                        e.printStackTrace();
                    }
                }
                // Unit of time is 10 milliseconds in GIF.
                timeLength *= 10;
            }
            return timeLength;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ImageProcessingException e) {
            e.printStackTrace();
        }
        return timeLength;
    }

    private static void createSprite(File pokemonGIF) throws IOException {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        File gifFile = new File(pokemonGIF.toString());
        ImageInputStream stream = ImageIO.createImageInputStream(gifFile);
        underlay = ImageIO.read(new File(pokemonGIF.toString()));
        Graphics2D underlayGraphics = underlay.createGraphics();
        underlayGraphics.setBackground(new Color(0, 0, 0, 0));
        underlayGraphics.clearRect(0, 0, underlay.getWidth(), underlay.getHeight());
        reader.setInput(stream);
        ImageFrame[] gifFrames = readGIF(reader);
        BufferedImage[] gifArray = new BufferedImage[gifFrames.length];
        int count = 0;
        for (ImageFrame frame : gifFrames) {
            gifArray[count] = frame.getImage();
            count++;
        }
        InputStream gifStream = new FileInputStream(pokemonGIF);
        frameTime = getGifAnimatedTimeLength(gifStream);
        HashMap<Integer,Integer> gifData = new HashMap();
        gifData.put(count,frameTime);

        pokemonFrames.put(pokemonGIF.getName().replace(".gif", ""), gifData);
        List<BufferedImage> gifList = Arrays.asList(gifArray);
        BufferedImage finalImage = joinBufferedImage(gifList);
        ImageIO.write(finalImage, "PNG", new File("assets/output/" + gifFile.getName().replace(".gif", "") + ".png"));
    }

    public static HashMap<String, HashMap<Integer, Integer>> getPokemonFrames() {
        return pokemonFrames;
    }

    public static BufferedImage joinBufferedImage(List<BufferedImage> imageArray) {
        int width1 = underlay.getWidth() * imageArray.size();
        int height1 = underlay.getHeight();

        BufferedImage newImage = new BufferedImage(width1, height1,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D newImageGraphics = newImage.createGraphics();
        newImageGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
        newImageGraphics.setBackground(new Color(255, 255, 255, 0));
        newImageGraphics.clearRect(0, 0, width1, height1);
        newImageGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));

        newImageGraphics.drawImage(imageArray.get(0), null, 0, 0);

        for (int i = 1; i < imageArray.size(); i++) {
            int width2 = imageArray.get(i).getWidth();
            int height2 = imageArray.get(i).getHeight();
            int height = (height1 - height2) / 2;
            int width = (underlay.getWidth() - width2) / 2;
            newImageGraphics.drawImage(imageArray.get(i), null, (underlay.getWidth() * i), 0);
        }
        newImageGraphics.dispose();
        return newImage;
    }

    private static class ImageFrame {
        private final int delay;
        private final BufferedImage image;
        private final String disposal;

        public ImageFrame(BufferedImage image, int delay, String disposal) {
            this.image = image;
            this.delay = delay;
            this.disposal = disposal;
        }

        public BufferedImage getImage() {
            return image;
        }

        public int getDelay() {
            return delay;
        }

        public String getDisposal() {
            return disposal;
        }
    }

    private static ImageFrame[] readGIF(ImageReader reader) throws IOException {
        ArrayList<ImageFrame> frames = new ArrayList<ImageFrame>(2);

        int width = -1;
        int height = -1;

        IIOMetadata metadata = reader.getStreamMetadata();
        if (metadata != null) {
            IIOMetadataNode globalRoot = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

            NodeList globalScreenDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor");

            if (globalScreenDescriptor != null && globalScreenDescriptor.getLength() > 0) {
                IIOMetadataNode screenDescriptor = (IIOMetadataNode) globalScreenDescriptor.item(0);

                if (screenDescriptor != null) {
                    width = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"));
                    height = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"));
                }
            }
        }

        BufferedImage master = null;
        Graphics2D masterGraphics = null;

        for (int frameIndex = 0; ; frameIndex++) {
            BufferedImage image;
            try {
                image = reader.read(frameIndex);
            } catch (IndexOutOfBoundsException io) {
                break;
            }

            if (width == -1 || height == -1) {
                width = image.getWidth();
                height = image.getHeight();
            }

            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(frameIndex).getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
            int delay = Integer.parseInt(gce.getAttribute("delayTime"));
            String disposal = gce.getAttribute("disposalMethod");

            int x = 0;
            int y = 0;

            if (master == null) {
                master = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                masterGraphics = master.createGraphics();
                masterGraphics.setBackground(new Color(0, 0, 0, 0));
            } else {
                NodeList children = root.getChildNodes();
                for (int nodeIndex = 0; nodeIndex < children.getLength(); nodeIndex++) {
                    Node nodeItem = children.item(nodeIndex);
                    if (nodeItem.getNodeName().equals("ImageDescriptor")) {
                        NamedNodeMap map = nodeItem.getAttributes();
                        x = Integer.parseInt(map.getNamedItem("imageLeftPosition").getNodeValue());
                        y = Integer.parseInt(map.getNamedItem("imageTopPosition").getNodeValue());
                    }
                }
            }
            masterGraphics.drawImage(image, x, y, null);

            BufferedImage copy = new BufferedImage(master.getColorModel(), master.copyData(null), master.isAlphaPremultiplied(), null);
            frames.add(new ImageFrame(copy, delay, disposal));

            if (disposal.equals("restoreToPrevious")) {
                BufferedImage from = null;
                for (int i = frameIndex - 1; i >= 0; i--) {
                    if (!frames.get(i).getDisposal().equals("restoreToPrevious") || frameIndex == 0) {
                        from = frames.get(i).getImage();
                        break;
                    }
                }

                if (from != null) {
                    master = new BufferedImage(from.getColorModel(), from.copyData(null), from.isAlphaPremultiplied(), null);
                }
                masterGraphics = master.createGraphics();
                masterGraphics.setBackground(new Color(0, 0, 0, 0));
            } else if (disposal.equals("restoreToBackgroundColor")) {
                masterGraphics.clearRect(x, y, image.getWidth(), image.getHeight());
            }
        }
        reader.dispose();

        return frames.toArray(new ImageFrame[0]);
    }
}
