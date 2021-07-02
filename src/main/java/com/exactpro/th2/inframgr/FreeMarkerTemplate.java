/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.inframgr;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

public class FreeMarkerTemplate {

    public static String getHrTemplate(String templateName, String hrName, String schemaName) {
        Logger logger = LoggerFactory.getLogger(FreeMarkerTemplate.class);

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setNumberFormat("computer");

        StringWriter stringWriter = new StringWriter();
        try {
            cfg.setDirectoryForTemplateLoading(new File(Objects.requireNonNull(
                    FreeMarkerTemplate.class.getClassLoader().getResource(templateName),
                    "The file " + templateName + " does not exist").getFile()).getParentFile());

            Template temp = cfg.getTemplate(templateName);
            temp.process(new HrConfigForTemplate(hrName), stringWriter);

        } catch (TemplateException te) {
            logger.error("Exception processing the template \"{}\"", templateName);
            throw new RuntimeException("Template processing exception", te);
        } catch (IOException ioe) {
            logger.error("Exception working on the template \"{}\"", templateName);
            throw new RuntimeException(ioe);
        }

        return stringWriter.toString();
    }
}
