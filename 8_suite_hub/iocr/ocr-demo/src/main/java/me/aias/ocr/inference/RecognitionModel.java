package me.aias.ocr.inference;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.util.NDImageUtils;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.paddlepaddle.zoo.cv.objectdetection.PpWordDetectionTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import me.aias.ocr.model.TableResult;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 关于如何优化性能，请参考ocr_sdk中的多线程处理，以及： http://aias.top/AIAS/guides/performance.html
 * @author Calvin
 * @date Oct 19, 2021
 */
public final class RecognitionModel {
    private ZooModel<Image, DetectedObjects> detModel;
    private ZooModel<Image, String> recModel;

    public void init(String detUri, String recUri) throws MalformedModelException, ModelNotFoundException, IOException {
        this.detModel = ModelZoo.loadModel(detectCriteria(detUri));
        this.recModel = ModelZoo.loadModel(recognizeCriteria(recUri));
    }

    public ZooModel<Image, DetectedObjects> getDetModel() {
        return detModel;
    }

    public ZooModel<Image, String> getRecModel() {
        return recModel;
    }

    public void close() {
        this.detModel.close();
        this.recModel.close();
    }

    private Criteria<Image, DetectedObjects> detectCriteria(String detUri) {
        Criteria<Image, DetectedObjects> criteria =
                Criteria.builder()
                        .optEngine("PaddlePaddle")
                        .setTypes(Image.class, DetectedObjects.class)
                        .optModelUrls(detUri)
                        .optTranslator(new PpWordDetectionTranslator(new ConcurrentHashMap<String, String>()))
                        .optProgress(new ProgressBar())
                        .build();

        return criteria;
    }

    private Criteria<Image, String> recognizeCriteria(String recUri) {
        Criteria<Image, String> criteria =
                Criteria.builder()
                        .optEngine("PaddlePaddle")
                        .setTypes(Image.class, String.class)
                        .optModelUrls(recUri)
                        .optProgress(new ProgressBar())
                        .optTranslator(new PpWordRecognitionTranslator())
                        .build();

        return criteria;
    }

    public String predictSingleLineText(Image image)
            throws TranslateException {
        try (Predictor<Image, String> recognizer = recModel.newPredictor()) {
            String text = recognizer.predict(image);
            return text;
        }
    }

    public DetectedObjects predict(Image image)
            throws TranslateException {
        try (Predictor<Image, String> recognizer = recModel.newPredictor();
             Predictor<Image, DetectedObjects> detector = detModel.newPredictor()) {

            DetectedObjects detections = detector.predict(image);
            List<DetectedObjects.DetectedObject> boxes = detections.items();
            List<String> names = new ArrayList<>();
            List<Double> prob = new ArrayList<>();
            List<BoundingBox> rect = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                Image subImg = getSubImage(image, boxes.get(i).getBoundingBox());
                if (subImg.getHeight() * 1.0 / subImg.getWidth() > 1.5) {
                    subImg = rotateImg(subImg);
                }
                String name = recognizer.predict(subImg);
                System.out.println(name);
                names.add(name);
                prob.add(-1.0);
                rect.add(boxes.get(i).getBoundingBox());
            }
            DetectedObjects detectedObjects = new DetectedObjects(names, prob, rect);
            return detectedObjects;

        }
    }

    private Image getSubImage(Image img, BoundingBox box) {
        Rectangle rect = box.getBounds();
        double[] extended = extendRect(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        int width = img.getWidth();
        int height = img.getHeight();
        int[] recovered = {
                (int) (extended[0] * width),
                (int) (extended[1] * height),
                (int) (extended[2] * width),
                (int) (extended[3] * height)
        };
        return img.getSubImage(recovered[0], recovered[1], recovered[2], recovered[3]);
    }

    private Image rotateImg(Image image) {
        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray rotated = NDImageUtils.rotate90(image.toNDArray(manager), 1);
            return ImageFactory.getInstance().fromNDArray(rotated);
        }
    }

    private double[] extendRect(double xmin, double ymin, double width, double height) {
        double centerx = xmin + width / 2;
        double centery = ymin + height / 2;
        if (width > height) {
            width += height * 2.0;
            height *= 3.0;
        } else {
            height += width * 2.0;
            width *= 3.0;
        }
        double newX = centerx - width / 2 < 0 ? 0 : centerx - width / 2;
        double newY = centery - height / 2 < 0 ? 0 : centery - height / 2;
        double newWidth = newX + width > 1 ? 1 - newX : width;
        double newHeight = newY + height > 1 ? 1 - newY : height;
        return new double[]{newX, newY, newWidth, newHeight};
    }
}
