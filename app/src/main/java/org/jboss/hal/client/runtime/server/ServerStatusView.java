/*
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.hal.client.runtime.server;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import javax.inject.Inject;

import com.google.common.base.Splitter;
import elemental.dom.Element;
import org.jboss.gwt.elemento.core.Elements;
import org.jboss.hal.ballroom.Format;
import org.jboss.hal.ballroom.LabelBuilder;
import org.jboss.hal.ballroom.LayoutBuilder;
import org.jboss.hal.ballroom.VerticalNavigation;
import org.jboss.hal.ballroom.form.Decoration;
import org.jboss.hal.ballroom.form.Form;
import org.jboss.hal.ballroom.form.ListItem;
import org.jboss.hal.ballroom.form.ReadOnlyAppearance;
import org.jboss.hal.ballroom.form.TextBoxItem;
import org.jboss.hal.ballroom.table.ColumnBuilder;
import org.jboss.hal.ballroom.table.DataTable;
import org.jboss.hal.ballroom.table.Options;
import org.jboss.hal.ballroom.table.OptionsBuilder;
import org.jboss.hal.core.mbui.form.ModelNodeForm;
import org.jboss.hal.core.mvp.HalViewImpl;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.Property;
import org.jboss.hal.meta.Metadata;
import org.jboss.hal.meta.MetadataRegistry;
import org.jboss.hal.resources.Ids;
import org.jboss.hal.resources.Names;
import org.jboss.hal.resources.Resources;

import static org.jboss.hal.ballroom.form.Decoration.RESTRICTED;
import static org.jboss.hal.client.runtime.server.ServerStatusPresenter.*;
import static org.jboss.hal.dmr.ModelDescriptionConstants.NAME;
import static org.jboss.hal.dmr.ModelDescriptionConstants.VALUE;
import static org.jboss.hal.resources.CSS.*;

/**
 * @author Harald Pehl
 */
public class ServerStatusView extends HalViewImpl implements ServerStatusPresenter.MyView {

    private static class PreReadOnlyAppearance<T> extends ReadOnlyAppearance<T> {

        PreReadOnlyAppearance() {
            super(EnumSet.of(RESTRICTED));

            // @formatter:off
            Elements.Builder builder = new Elements.Builder()
                .div().css(formGroup)
                    .label().css(controlLabel, halFormLabel).rememberAs(LABEL_ELEMENT).end()
                    .div().css(halFormInput)
                        .start("pre").css(formControlStatic).rememberAs(VALUE_ELEMENT).end()
                    .end()
                .end();
            // @formatter:on

            valueElement = builder.referenceFor(VALUE_ELEMENT);
        }

        @Override
        protected String name() {
            return "PreReadOnlyAppearance";
        }

        @Override
        protected <C> void safeApply(final Decoration decoration, final C context) {
            if (decoration == RESTRICTED) {
                valueElement.setTextContent("");
                valueElement.setTextContent(CONSTANTS.restricted());
            }
        }
    }


    private static class PreTextItem extends TextBoxItem {

        PreTextItem(String name) {
            super(name, new LabelBuilder().label(name));

            // replace read-only appearance
            addAppearance(Form.State.READONLY, new PreReadOnlyAppearance<>());
        }
    }


    private static class PreListItem extends ListItem {

        PreListItem(String name) {
            super(name, new LabelBuilder().label(name));

            // replace read-only appearance
            addAppearance(Form.State.READONLY, new PreReadOnlyAppearance<List<String>>() {
                @Override
                public String asString(final List<String> value) {
                    return String.join("\n", value);
                }
            });
        }
    }


    private static final String HEADER_ELEMENT = "headerElement";

    private static final String[] MAIN_ATTRIBUTES = {
            NAME,
            "vm-name",
            "vm-vendor",
            "vm-version",
            "spec-name",
            "spec-vendor",
            "spec-version",
            "management-spec-version",
            START_TIME,
            UPTIME
    };

    private static final String[] BOOTSTRAP_ATTRIBUTES = {
            "boot-class-path-supported",
            BOOT_CLASS_PATH,
            CLASS_PATH,
            LIBRARY_PATH,
            INPUT_ARGUMENTS,
    };

    private final Form<ModelNode> mainAttributes;
    private final Form<ModelNode> bootstrapAttributes;
    private final DataTable<Property> systemProperties;
    private final Element headerElement;

    @Inject
    public ServerStatusView(final MetadataRegistry metadataRegistry, final Resources resources) {
        Metadata metadata = metadataRegistry.lookup(ServerStatusPresenter.SERVER_STATUS_TEMPLATE);
        mainAttributes = new ModelNodeForm.Builder<>(Ids.SERVER_STATUS_MAIN_ATTRIBUTES_FORM, metadata)
                .viewOnly()
                .includeRuntime()
                .include(MAIN_ATTRIBUTES)
                .unsorted()
                .build();

        bootstrapAttributes = new ModelNodeForm.Builder<>(Ids.SERVER_STATUS_BOOTSTRAP_FORM, metadata)
                .viewOnly()
                .includeRuntime()
                .include(BOOTSTRAP_ATTRIBUTES)
                .customFormItem(BOOT_CLASS_PATH, attributeDescription -> new PreTextItem(BOOT_CLASS_PATH))
                .customFormItem(CLASS_PATH, attributeDescription -> new PreTextItem(CLASS_PATH))
                .customFormItem(LIBRARY_PATH, attributeDescription -> new PreTextItem(LIBRARY_PATH))
                .customFormItem(INPUT_ARGUMENTS, attributeDescription -> new PreListItem(INPUT_ARGUMENTS))
                .unsorted()
                .build();

        Options<Property> options = new OptionsBuilder<Property>()
                .column(NAME, Names.NAME, (cell, t, row, meta) -> row.getName())
                .column(new ColumnBuilder<Property>(VALUE, Names.VALUE,
                        (cell, t, row, meta) -> row.getValue().asString())
                        .width("66%")
                        .searchable(false)
                        .orderable(false)
                        .build())
                .build();
        systemProperties = new DataTable<>(Ids.SERVER_STATUS_SYSTEM_PROPERTIES_TABLE, options);

        registerAttachable(mainAttributes, bootstrapAttributes, systemProperties);

        // @formatter:off
        Elements.Builder mainAttributesBuilder = new Elements.Builder()
            .section()
                .h(1).rememberAs(HEADER_ELEMENT).end()
                .p().textContent(metadata.getDescription().getDescription()).end()
                .add(mainAttributes)
            .end();
        headerElement = mainAttributesBuilder.referenceFor(HEADER_ELEMENT);

        Element bootstrapSection =  new Elements.Builder()
            .section()
                .h(1).textContent(Names.BOOTSTRAP).end()
                .add(bootstrapAttributes)
            .end()
        .build();

        Element systemPropertiesSection =  new Elements.Builder()
            .section()
                .h(1).textContent(Names.SYSTEM_PROPERTIES).end()
                .add(systemProperties)
            .end()
        .build();
        // @formatter:on

        VerticalNavigation navigation = new VerticalNavigation();
        registerAttachable(navigation);

        navigation.addPrimary(Ids.SERVER_STATUS_MAIN_ATTRIBUTES_ENTRY, resources.constants().mainAttributes(),
                fontAwesome("list-ul"), mainAttributesBuilder.<Element>build());
        navigation
                .addPrimary(Ids.SERVER_STATUS_BOOTSTRAP_ENTRY, Names.BOOTSTRAP, fontAwesome("play"), bootstrapSection);
        navigation
                .addPrimary(Ids.SERVER_STATUS_SYSTEM_PROPERTIES_ENTRY, Names.SYSTEM_PROPERTIES, pfIcon("resource-pool"),
                        systemPropertiesSection);

        LayoutBuilder layoutBuilder = new LayoutBuilder()
                .row()
                .column()
                .addAll(navigation.panes())
                .end()
                .end();
        Element root = layoutBuilder.build();
        initElement(root);
    }

    @Override
    public void update(final ModelNode modelNode) {
        List<Property> sp = modelNode.get(SYSTEM_PROPERTIES).asPropertyList();
        String pathSeparator = sp.stream()
                .filter(p -> "path.separator".equals(p.getName())) //NON-NLS
                .findAny()
                .map(p -> p.getValue().asString())
                .orElse(":");

        headerElement.setTextContent(modelNode.get(NAME).asString());

        mainAttributes.view(modelNode);
        mainAttributes.getFormItem(START_TIME)
                .setText(Format.shortDateTime(new Date(modelNode.get(START_TIME).asLong())));
        mainAttributes.getFormItem(UPTIME).setText(Format.humanReadableDuration(modelNode.get(UPTIME).asLong()));

        bootstrapAttributes.view(modelNode);
        bootstrapAttributes.getFormItem(BOOT_CLASS_PATH)
                .setText(pathWithNewLines(modelNode.get(BOOT_CLASS_PATH).asString(), pathSeparator));
        bootstrapAttributes.getFormItem(CLASS_PATH)
                .setText(pathWithNewLines(modelNode.get(CLASS_PATH).asString(), pathSeparator));
        bootstrapAttributes.getFormItem(LIBRARY_PATH)
                .setText(pathWithNewLines(modelNode.get(LIBRARY_PATH).asString(), pathSeparator));

        systemProperties.update(sp, Property::getName);
    }

    private String pathWithNewLines(String path, String pathSeparator) {
        return String.join("\n", Splitter.on(pathSeparator).omitEmptyStrings().split(path));
    }
}