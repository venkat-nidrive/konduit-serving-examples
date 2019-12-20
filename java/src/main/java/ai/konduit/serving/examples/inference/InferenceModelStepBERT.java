/*
 *
 *  * ******************************************************************************
 *  *  * Copyright (c) 2019 Konduit AI.
 *  *  * This program and the accompanying materials are made available under the
 *  *  * terms of the Apache License, Version 2.0 which is available at
 *  *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *  *  Unless required by applicable law or agreed to in writing, software
 *  *  *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  *  * License for the specific language governing permissions and limitations
 *  *  * under the License.
 *  *  * SPDX-License-Identifier: Apache-2.0
 *  *  *****************************************************************************
 *
 *
 */

package ai.konduit.serving.examples.inference;

import ai.konduit.serving.InferenceConfiguration;
import ai.konduit.serving.config.Input;
import ai.konduit.serving.config.Output;
import ai.konduit.serving.config.ParallelInferenceConfig;
import ai.konduit.serving.config.ServingConfig;
import ai.konduit.serving.configprovider.KonduitServingMain;
import ai.konduit.serving.model.ModelConfig;
import ai.konduit.serving.model.ModelConfigType;
import ai.konduit.serving.model.TensorDataType;
import ai.konduit.serving.model.TensorDataTypesConfig;
import ai.konduit.serving.model.TensorFlowConfig;
import ai.konduit.serving.pipeline.step.ModelStep;
import org.apache.commons.io.FileUtils;
import org.nd4j.linalg.io.ClassPathResource;


import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

@NotThreadSafe
public class InferenceModelStepBERT {
    public static void main(String[] args) throws Exception {
        String bertmodelfilePath = new ClassPathResource("data/bert/bert_mrpc_frozen.pb").getFile().getAbsolutePath();

        HashMap<String, TensorDataType> input_data_types=new HashMap();
        input_data_types.put("IteratorGetNext:0", TensorDataType.INT32);
        input_data_types.put("IteratorGetNext:1", TensorDataType.INT32);
        input_data_types.put("IteratorGetNext:4", TensorDataType.INT32);

        ModelConfig bertModelConfig = TensorFlowConfig.builder()
                .tensorDataTypesConfig(TensorDataTypesConfig.builder().
                        inputDataTypes(input_data_types).build())
                .modelConfigType(ModelConfigType.builder().
                        modelLoadingPath(bertmodelfilePath.toString()).
                        modelType(ModelConfig.ModelType.TENSORFLOW).build())
                .build();

        List<String> input_names = new ArrayList<String>(input_data_types.keySet());
        ArrayList<String> output_names=new ArrayList<>();
        output_names.add("loss/Softmax");
        int port = Util.randInt(1000, 65535);

        ModelStep bertModelStep = ModelStep.builder()
                .modelConfig(bertModelConfig)
                .inputNames(input_names)
                .outputNames(output_names)
                .parallelInferenceConfig(ParallelInferenceConfig.builder().workers(1).build())
                .build();

        ServingConfig servingConfig = ServingConfig.builder().httpPort(port).
                inputDataFormat(Input.DataFormat.NUMPY).
                outputDataFormat(Output.DataFormat.NUMPY).
                build();

        InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
                .servingConfig(servingConfig)
                .step(bertModelStep)
                .build();

        System.out.println(inferenceConfiguration.toJson());

        File configFile = new File("config.json");
        FileUtils.write(configFile, inferenceConfiguration.toJson(), Charset.defaultCharset());
        KonduitServingMain.main("--configPath", configFile.getAbsolutePath());

        Thread.sleep(3600000);
    }
}
