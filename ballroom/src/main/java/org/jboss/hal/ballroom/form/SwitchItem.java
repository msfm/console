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
package org.jboss.hal.ballroom.form;

import java.util.EnumSet;

import com.google.common.base.Strings;
import com.google.web.bindery.event.shared.HandlerRegistration;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLInputElement;
import org.jboss.gwt.elemento.core.Elements;
import org.jboss.hal.resources.CSS;
import org.jboss.hal.resources.Ids;

import static org.jboss.gwt.elemento.core.Elements.i;
import static org.jboss.gwt.elemento.core.Elements.*;
import static org.jboss.gwt.elemento.core.EventType.bind;
import static org.jboss.gwt.elemento.core.EventType.change;
import static org.jboss.gwt.elemento.core.EventType.click;
import static org.jboss.gwt.elemento.core.InputType.checkbox;
import static org.jboss.gwt.elemento.core.InputType.text;
import static org.jboss.hal.ballroom.form.Decoration.*;
import static org.jboss.hal.ballroom.form.Form.State.EDITING;
import static org.jboss.hal.resources.CSS.*;

public class SwitchItem extends AbstractFormItem<Boolean> {

    private final SwitchEditingAppearance editingAppearance;

    public SwitchItem(String name, String label) {
        super(name, label, null);

        // read-only appearance
        addAppearance(Form.State.READONLY, new SwitchReadOnlyAppearance());

        // editing appearance
        editingAppearance = new SwitchEditingAppearance();
        addAppearance(Form.State.EDITING, editingAppearance);
    }

    @Override
    public boolean isEmpty() {
        return isExpressionValue() ? Strings.isNullOrEmpty(getExpressionValue()) : getValue() == null;
    }

    @Override
    public boolean supportsExpressions() {
        return true;
    }

    @Override
    public void setExpressionAllowed(boolean expressionAllowed) {
        super.setExpressionAllowed(expressionAllowed);
        editingAppearance.setExpressionAllowed(expressionAllowed);
    }


    private static class SwitchReadOnlyAppearance extends ReadOnlyAppearance<Boolean> {

        SwitchReadOnlyAppearance() {
            super(EnumSet.of(DEFAULT, DEPRECATED, EXPRESSION, RESTRICTED));
        }

        @Override
        protected String name() {
            return "SwitchReadOnlyAppearance";
        }
    }


    private class SwitchEditingAppearance extends EditingAppearance<Boolean> {

        private final HTMLElement normalModeContainer;
        private final HTMLElement switchToExpressionButton;
        private final HTMLInputElement expressionModeInput;
        private final HTMLElement resolveExpressionButton;
        private final HTMLElement expressionModeContainer;
        private final FormItemValidation<Boolean> expressionValidation;
        private Boolean backup;
        private HandlerRegistration expressionHandler;
        private HandlerRegistration resolveHandler;


        // ------------------------------------------------------ ui code

        SwitchEditingAppearance() {
            super(EnumSet.of(DEFAULT, DEPRECATED, ENABLED, EXPRESSION, INVALID, REQUIRED, RESTRICTED),
                    input(checkbox).get());

            // put the <input type="checkbox"/> into an extra div
            // this makes switching between normal and expression mode easier
            inputContainer.removeChild(inputElement);
            normalModeContainer = div().get();
            normalModeContainer.appendChild(inputElement);
            inputContainer.appendChild(normalModeContainer);

            expressionModeContainer = div().css(CSS.inputGroup)
                    .add(span().css(inputGroupBtn)
                            .add(button()
                                    .css(btn, btnDefault, expressionModeSwitcher)
                                    .style("margin-right: -2px")
                                    .title(CONSTANTS.switchToNormalMode())
                                    .on(click, event -> switchToNormalMode())
                                    .add(i().css(fontAwesome("toggle-on")))))
                    .add(expressionModeInput = input(text)
                            .css(formControl)
                            .apply(input -> input.placeholder = CONSTANTS.expression())
                            .get())
                    .add(span().css(inputGroupBtn)
                            .add(resolveExpressionButton = button()
                                    .css(btn, btnDefault)
                                    .title(CONSTANTS.resolveExpression())
                                    .add(i().css(fontAwesome("link")))
                                    .get()))
                    .get();

            switchToExpressionButton = button()
                    .css(btn, btnDefault, expressionModeSwitcher)
                    .title(CONSTANTS.switchToExpressionMode())
                    .on(click, event -> switchToExpressionMode())
                    .add(i().css(fontAwesome("link")))
                    .get();
            expressionHandler = bind(expressionModeInput, change,
                    event -> modifyExpressionValue(expressionModeInput.value));

            // it's types to boolean, but used to validate the expression
            this.expressionValidation = value -> {
                if (!hasExpressionScheme(expressionModeInput.value)) {
                    return ValidationResult.invalid(CONSTANTS.invalidExpression());
                }
                return ValidationResult.OK;
            };
        }

        private void switchToNormalMode() {
            unapply(INVALID);
            unapply(EXPRESSION);
            if (backup != null) {
                modifyValue(backup);
            }
        }

        private void switchToExpressionMode() {
            backup = getValue();
            applyExpressionValue(expressionModeInput.value);
            modifyExpressionValue(expressionModeInput.value);
        }

        @Override
        public void attach() {
            super.attach();
            HTMLElement editElement = SwitchItem.this.element(EDITING);
            if (Elements.isVisible(editElement) && editElement.querySelector("." + bootstrapSwitchContainer) == null) {
                inputElement.classList.add(bootstrapSwitch);
                SwitchBridge.Api api = SwitchBridge.Api.element(inputElement);
                api.bootstrapSwitch();
                api.onChange((event, state) -> modifyValue(state));
            }
        }

        @Override
        public void detach() {
            super.detach();
            if (attached) {
                if (expressionHandler != null) {
                    expressionHandler.removeHandler();
                }
                if (resolveHandler != null) {
                    resolveHandler.removeHandler();
                }
                inputElement.classList.remove(bootstrapSwitch);
                SwitchBridge.Api.element(inputElement).destroy();
            }
        }

        @Override
        protected String name() {
            return "SwitchEditingAppearance";
        }


        // ------------------------------------------------------ value

        @Override
        public void showValue(Boolean value) {
            if (attached) {
                SwitchBridge.Api.element(inputElement).setValue(value);
            } else {
                inputElement.checked = value;
            }
        }

        @Override
        public void showExpression(String expression) {
            expressionModeInput.value = expression;
        }

        @Override
        public void clearValue() {
            if (attached) {
                SwitchBridge.Api.element(inputElement).setValue(false);
            } else {
                inputElement.checked = false;
            }
        }


        // ------------------------------------------------------ decorations

        @Override
        void applyDefault(String defaultValue) {
            if (attached) {
                SwitchBridge.Api.element(inputElement).setValue(Boolean.parseBoolean(defaultValue));
            } else {
                inputElement.checked = Boolean.parseBoolean(defaultValue);
            }
        }

        @Override
        protected void applyExpression(ExpressionContext expressionContext) {
            Elements.failSafeRemove(inputContainer, normalModeContainer);
            Elements.lazyAppend(inputContainer, expressionModeContainer);
            if (resolveHandler == null) {
                resolveHandler = bind(resolveExpressionButton, click,
                        event -> expressionContext.callback.resolveExpression(expressionModeInput.value));
            }
            addValidationHandler(expressionValidation);
        }

        @Override
        void unapplyExpression() {
            if (resolveHandler != null) {
                resolveHandler.removeHandler();
                resolveHandler = null;
            }
            Elements.failSafeRemove(inputContainer, expressionModeContainer);
            Elements.lazyAppend(inputContainer, normalModeContainer);
            removeValidationHandler(expressionValidation);
        }

        void setExpressionAllowed(boolean expressionAllowed) {
            Elements.setVisible(switchToExpressionButton, expressionAllowed);
            if (expressionAllowed) {
                Elements.lazyInsertBefore(normalModeContainer, switchToExpressionButton,
                        normalModeContainer.firstElementChild);
            } else {
                Elements.failSafeRemove(normalModeContainer, switchToExpressionButton);
            }
        }


        // ------------------------------------------------------ properties & delegates

        @Override
        public void setId(String id) {
            super.setId(id);
            // the checkbox item and the expression input have the same id
            // make sure only one is part of the DOM!
            expressionModeInput.id = Ids.build(id, EDITING.name().toLowerCase());
        }

        @Override
        public void setName(String name) {
            inputElement.name = name;
            expressionModeInput.name = name;
        }

        @Override
        public int getTabIndex() {
            if (isApplied(EXPRESSION)) {
                return (int) expressionModeInput.tabIndex;
            } else {
                return (int) inputElement.tabIndex;
            }
        }

        @Override
        public void setAccessKey(char key) {
            super.setAccessKey(key);
            expressionModeInput.accessKey = String.valueOf(key);
        }

        @Override
        public void setFocus(boolean focused) {
            if (isApplied(EXPRESSION)) {
                if (focused) {
                    expressionModeInput.focus();
                } else {
                    expressionModeInput.blur();
                }
            } else {
                if (focused) {
                    inputElement.focus();
                } else {
                    inputElement.blur();
                }
            }
        }

        @Override
        public void setTabIndex(int index) {
            super.setTabIndex(index);
            expressionModeInput.tabIndex = index;
        }
    }
}
