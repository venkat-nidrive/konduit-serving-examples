/*
 *       Copyright (c) 2019 Konduit AI.
 *
 *       This program and the accompanying materials are made available under the
 *       terms of the Apache License, Version 2.0 which is available at
 *       https://www.apache.org/licenses/LICENSE-2.0.
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *       WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *       License for the specific language governing permissions and limitations
 *       under the License.
 *
 *       SPDX-License-Identifier: Apache-2.0
 *
 */

package ai.konduit.serving.examples.inference;

import ai.konduit.serving.InferenceConfiguration;
import ai.konduit.serving.config.ParallelInferenceConfig;
import ai.konduit.serving.config.ServingConfig;
import ai.konduit.serving.configprovider.KonduitServingMain;
import ai.konduit.serving.configprovider.KonduitServingMainArgs;
import ai.konduit.serving.model.ModelConfig;
import ai.konduit.serving.model.ModelConfigType;
import ai.konduit.serving.model.TensorDataTypesConfig;
import ai.konduit.serving.model.TensorFlowConfig;
import ai.konduit.serving.pipeline.step.ModelStep;
import ai.konduit.serving.verticles.inference.InferenceVerticle;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.io.ClassPathResource;
import org.nd4j.tensorflow.conversion.TensorDataType;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * Example for Inference for BERT ML model using Model step .
 * This illustrates only the server configuration and start server.
 */
@NotThreadSafe
public class InferenceModelStepBERT {
    public static void main(String[] args) throws Exception {

        //File path for model
        String bertDataFolder = new ClassPathResource("data/bert").getFile().getAbsolutePath();

        String bertFileName = "bert_mrpc_frozen.pb";
        File bertModelFile = new File(bertDataFolder, bertFileName);

        // If bert_mrpc_frozen file doesn't exist, download it and unzip it to target folder.
        // This might take several minutes depending on the internet speed.
        if (!bertModelFile.exists()) {
            File bertDownloadedZipFile = Util.downloadBertModel();
            Util.unzipBertFile(bertDownloadedZipFile.toString(), bertFileName);
        }

        //Set the tensor input data types
        HashMap<String, TensorDataType> input_data_types = new LinkedHashMap<>();
        input_data_types.put("IteratorGetNext:0", TensorDataType.INT32);
        input_data_types.put("IteratorGetNext:1", TensorDataType.INT32);
        input_data_types.put("IteratorGetNext:4", TensorDataType.INT32);

        //Model config and set model type as BERT
        ModelConfig bertModelConfig = TensorFlowConfig.builder()
                .tensorDataTypesConfig(TensorDataTypesConfig.builder().
                        inputDataTypes(input_data_types).build())
                .modelConfigType(ModelConfigType.builder().
                        modelLoadingPath(bertModelFile.getAbsolutePath()).
                        modelType(ModelConfig.ModelType.TENSORFLOW).build())
                .build();

        //Set the input and output names for model step
        List<String> input_names = new ArrayList<>(input_data_types.keySet());
        ArrayList<String> output_names = new ArrayList<>();
        output_names.add("loss/Softmax");

        //Set the configuration of model to step
        ModelStep bertModelStep = ModelStep.builder()
                .modelConfig(bertModelConfig)
                .inputNames(input_names)
                .outputNames(output_names)
                .parallelInferenceConfig(ParallelInferenceConfig.builder().workers(1).build())
                .build();

        //ServingConfig set httpport and Input Formats
        int port = Util.randInt(1000, 65535);
        ServingConfig servingConfig = ServingConfig.builder()
                .httpPort(port)
                .build();

        //Inference Configuration
        InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
                .servingConfig(servingConfig)
                .step(bertModelStep)
                .build();

        //Print the configuration to make sure our settings correctly set.
        System.out.println(inferenceConfiguration.toJson());

        File configFile = new File("config.json");
        FileUtils.write(configFile, inferenceConfiguration.toJson(), Charset.defaultCharset());

        //Start inference server as per the above configurations
        KonduitServingMainArgs args1 = KonduitServingMainArgs.builder()
                .configStoreType("file").ha(false)
                .multiThreaded(false).configPort(port)
                .verticleClassName(InferenceVerticle.class.getName())
                .configPath(configFile.getAbsolutePath())
                .build();

        File input0 = new ClassPathResource("data/bert/input-0.npy").getFile();
        File input1 = new ClassPathResource("data/bert/input-1.npy").getFile();
        File input4 = new ClassPathResource("data/bert/input-4.npy").getFile();

        KonduitServingMain.builder()
                .onSuccess(() -> {
                    try {
                        //client config.
                        String response = Unirest.post(String.format("http://localhost:%s/raw/numpy", port))
                                .field("IteratorGetNext:0", input0)
                                .field("IteratorGetNext:1", input1)
                                .field("IteratorGetNext:4", input4)
                                .asString().getBody();
                        System.out.print(response);
                        System.exit(0);
                    } catch (UnirestException e) {
                        e.printStackTrace();
                        System.exit(0);
                    }
                })
                .build()
                .runMain(args1.toArgs());
    }
}
