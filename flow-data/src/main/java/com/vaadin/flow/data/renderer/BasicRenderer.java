/*
 * Copyright 2000-2017 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.flow.data.renderer;

import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.AbstractComponentDataGenerator;
import com.vaadin.flow.data.provider.DataGenerator;
import com.vaadin.flow.data.provider.DataKeyMapper;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.ValueProvider;

import elemental.json.JsonObject;

/**
 * 
 * Abstract renderer used as the base implementation for renderers that outputs
 * a simple value in the UI, such as {@link NumberRenderer} and
 * {@link LocalDateRenderer}.
 * 
 * @author Vaadin Ltd.
 *
 * @param <SOURCE>
 *            the type of the item used inside the renderer
 * @param <TARGET>
 *            the type of the output object, such as Number or LocalDate
 */
public abstract class BasicRenderer<SOURCE, TARGET>
        extends ComponentRenderer<Component, SOURCE> {

    private final ValueProvider<SOURCE, TARGET> valueProvider;

    /**
     * Builds a new template renderer using the value provider as the source of
     * values to be rendered.
     * 
     * @param valueProvider
     *            the callback to provide a objects to the renderer, not
     *            <code>null</code>
     */
    protected BasicRenderer(ValueProvider<SOURCE, TARGET> valueProvider) {
        if (valueProvider == null) {
            throw new IllegalArgumentException("valueProvider may not be null");
        }

        this.valueProvider = valueProvider;
    }

    protected ValueProvider<SOURCE, TARGET> getValueProvider() {
        return valueProvider;
    }

    @Override
    public Rendering<SOURCE> render(Element container,
            DataKeyMapper<SOURCE> keyMapper) {
        removeTemplates(container);
        SimpleValueRendering rendering = new SimpleValueRendering(
                keyMapper == null ? null : keyMapper::key);
        setupTemplate(container, rendering, keyMapper);

        return rendering;
    }

    private void setupTemplate(Element owner, SimpleValueRendering rendering,
            DataKeyMapper<SOURCE> keyMapper) {

        Element templateElement = new Element("template", false);
        rendering.setTemplateElement(templateElement);

        owner.getNode()
                .runWhenAttached(ui -> ui.getInternals().getStateTree()
                        .beforeClientResponse(owner.getNode(),
                                () -> setupTemplateWhenAttached(owner,
                                        rendering, keyMapper)));
    }

    private void setupTemplateWhenAttached(Element owner,
            SimpleValueRendering rendering, DataKeyMapper<SOURCE> keyMapper) {

        Element templateElement = rendering.getTemplateElement().get();
        owner.appendChild(templateElement);

        if (keyMapper != null) {
            String propertyName = getTemplatePropertyName(rendering);

            templateElement.setProperty("innerHTML", getTemplateForProperty(
                    "[[item." + propertyName + "]]", rendering));
            rendering.setPropertyName(propertyName);

            RendererUtil.registerEventHandlers(this, templateElement, owner,
                    keyMapper::get);
        } else {
            String value = getFormattedValue(null);
            templateElement.setProperty("innerHTML",
                    getTemplateForProperty(value, rendering));
            rendering.setContainer(owner);
        }
    }

    /**
     * Gets the name of the property to be transmitted and used inside the
     * template. By default, it generates a unique name by using the class name
     * of the renderer and the node id of the template element.
     * <p>
     * This method is only called when {@link #render(Element, DataKeyMapper)}
     * is invoked.
     * 
     * @param context
     *            the rendering context
     * @return the property name to be used in template data bindings
     * 
     * @see Rendering#getTemplateElement()
     */
    protected String getTemplatePropertyName(Rendering<SOURCE> context) {
        Objects.requireNonNull(context, "The context should not be null");
        if (!context.getTemplateElement().isPresent()) {
            throw new IllegalArgumentException(
                    "The provided rendering doesn't contain a template element");
        }
        Element templateElement = context.getTemplateElement().get();
        return "_" + getClass().getSimpleName() + "_"
                + templateElement.getNode().getId();
    }

    /**
     * Gets the template String for a given property.
     * <p>
     * This method is only called when {@link #render(Element, DataKeyMapper)}
     * is invoked.
     * 
     * @param property
     *            the property to be used inside the template
     * @param context
     *            the rendering context
     * @return the template string to be used inside a {@code <template>}
     *         element
     * 
     * @see #getTemplatePropertyName(Rendering)
     */
    protected String getTemplateForProperty(String property,
            Rendering<SOURCE> context) {
        return property == null ? "" : property;
    }

    @Override
    public Component createComponent(SOURCE item) {
        return new Span(getFormattedValue(valueProvider.apply(item)));
    }

    /**
     * Gets the String representation of the target object, to be used inside
     * the template.
     * <p>
     * By default it uses {@link String#valueOf(Object)} of the object.
     * 
     * @param object
     *            the target object
     * @return the string representation of the object
     */
    protected String getFormattedValue(TARGET object) {
        return String.valueOf(object);
    }

    private class SimpleValueRendering
            extends AbstractComponentDataGenerator<SOURCE>
            implements Rendering<SOURCE> {

        private final ValueProvider<SOURCE, String> keyMapper;
        private Element templateElement;
        private String propertyName;
        private Element container;

        public SimpleValueRendering(ValueProvider<SOURCE, String> keyMapper) {
            this.keyMapper = keyMapper;
        }

        public void setContainer(Element container) {
            this.container = container;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public void setTemplateElement(Element templateElement) {
            this.templateElement = templateElement;
        }

        @Override
        public Optional<Element> getTemplateElement() {
            return Optional.ofNullable(templateElement);
        }

        @Override
        public Optional<DataGenerator<SOURCE>> getDataGenerator() {
            return Optional.of(this);
        }

        @Override
        public void generateData(SOURCE item, JsonObject jsonObject) {
            if (propertyName != null) {
                String value = getFormattedValue(valueProvider.apply(item));
                if (value != null) {
                    jsonObject.put(propertyName, value);
                }
            } else if (container != null) {
                String itemKey = getItemKey(item);
                Component component = createComponent(item);
                registerRenderedComponent(itemKey, component);
            }
        }

        @Override
        public void refreshData(SOURCE item) {
            if (propertyName != null) {
                return;
            }
            super.refreshData(item);
        }

        @Override
        protected String getItemKey(SOURCE item) {
            if (keyMapper == null) {
                return null;
            }
            return keyMapper.apply(item);
        }

        @Override
        protected Component createComponent(SOURCE item) {
            return BasicRenderer.this.createComponent(item);
        }

        @Override
        protected Element getContainer() {
            return container;
        }
    }

}
