package me.aias.example.utils;

import ai.djl.Device;
import ai.djl.ndarray.NDArray;
import ai.djl.repository.zoo.Criteria;
import ai.djl.training.util.ProgressBar;

public class SpeakerEncoder {
  public SpeakerEncoder() {}

  public Criteria<NDArray, NDArray> criteria() {
    Criteria<NDArray, NDArray> criteria =
        Criteria.builder()
            .setTypes(NDArray.class, NDArray.class)
            .optModelUrls(
                "https://aias-home.oss-cn-beijing.aliyuncs.com/models/speech_models/speakerEncoder.zip")
            // .optModelUrls("/Users/calvin/ms-marco-TinyBERT-L-2-v2/")
            // .optModelPath(Paths.get("src/main/resources/voice/tacotronSTFT.pt"))
            .optTranslator(new SpeakerEncoderTranslator())
            .optEngine("PyTorch") // Use PyTorch engine
            .optOption("mapLocation", "true")
            .optProgress(new ProgressBar())
            .build();

    return criteria;
  }
}
