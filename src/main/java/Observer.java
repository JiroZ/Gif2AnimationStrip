import java.awt.*;
import java.awt.image.ImageObserver;

public class Observer implements ImageObserver {
    Observer(Image img) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.prepareImage(img, -1, -1, this);
    }
    int getFrameBits() {
        return this.FRAMEBITS;
    }

    @Override
    public boolean imageUpdate(Image image, int i, int i1, int i2, int i3, int i4) {
        return false;
    }
}
