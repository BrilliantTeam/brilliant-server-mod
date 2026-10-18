package net.brilliantw.richpresence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class EditDialog extends Screen {
	public record Field(String label, String hint, String value, int maxLength) {}

	public interface Saver {
		String save(List<String> values, int groupIndex);
	}

	private static final int WIDTH = 260;
	private static final int FIELD = 36;

	private final Screen parent;
	private final List<Field> fields;
	private final List<String> groups;
	private final Saver saver;
	private final String[] values;
	private int groupIndex;
	private String choiceLabel = "分組";
	private int commandIndex = -1;
	private CommandSuggestionBox suggestions;
	private String error = "";

	public EditDialog(Screen parent, String title, List<Field> fields, List<String> groups, int groupIndex, Saver saver) {
		super(Component.literal(title));
		this.parent = parent;
		this.fields = fields;
		this.groups = groups;
		this.groupIndex = groupIndex;
		this.saver = saver;
		this.values = fields.stream().map(Field::value).toArray(String[]::new);
	}

	public EditDialog command(int index) {
		commandIndex = index;
		return this;
	}

	public EditDialog choice(String label) {
		choiceLabel = label;
		return this;
	}

	private int dialogHeight() {
		return 28 + fields.size() * FIELD + (groups != null ? FIELD : 0) + 14 + 28;
	}

	private int left() {
		return (width - WIDTH) / 2;
	}

	private int top() {
		return (height - dialogHeight()) / 2;
	}

	@Override
	protected void init() {
		int x = left() + 10;
		int y = top() + 28;
		List<EditBox> boxes = new ArrayList<>();
		for (int i = 0; i < fields.size(); i++) {
			int index = i;
			Field field = fields.get(i);
			EditBox box = addRenderableWidget(new EditBox(font, x, y + 11, WIDTH - 20, 18, Component.literal(field.label())));
			box.setMaxLength(field.maxLength());
			Theme.hint(box, field.hint());
			box.setValue(values[i]);
			if (i == commandIndex) {
				CommandSuggestionBox helper = new CommandSuggestionBox(this, box);
				suggestions = helper;
				box.setResponder(value -> {
					values[index] = value;
					helper.update();
				});
			} else {
				box.setResponder(value -> values[index] = value);
			}
			boxes.add(box);
			y += FIELD;
		}
		if (groups != null) {
			addRenderableWidget(Button.builder(groupLabel(), button -> {
				groupIndex = (groupIndex + 1) % groups.size();
				button.setMessage(groupLabel());
			}).bounds(x, y + 11, WIDTH - 20, 20).build());
			y += FIELD;
		}
		int buttonY = top() + dialogHeight() - 28;
		int buttonWidth = (WIDTH - 26) / 2;
		addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose()).bounds(x, buttonY, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("儲存").withStyle(ChatFormatting.GREEN), b -> save()).bounds(x + buttonWidth + 6, buttonY, buttonWidth, 20).build());
		if (!boxes.isEmpty()) {
			setInitialFocus(boxes.getFirst());
		}
	}

	private Component groupLabel() {
		return Component.literal(choiceLabel + "：" + groups.get(groupIndex) + (groups.size() > 1 ? "（點擊切換）" : ""));
	}

	private void save() {
		String result = saver.save(List.of(values), groupIndex);
		if (result == null) {
			CommandMenuScreen.setScreen(parent);
		} else {
			error = result;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (suggestions != null && suggestions.keyPressed(event)) {
			return true;
		}
		if (event.isConfirmation()) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics));
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (suggestions != null) {
			suggestions.render(graphics, mouseX, mouseY);
		}
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		draw(new Gfx(graphics));
		super.render(graphics, mouseX, mouseY, partialTick);
		if (suggestions != null) {
			suggestions.render(graphics, mouseX, mouseY);
		}
	}
	*///?}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (suggestions != null && suggestions.mouseClicked(event)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (suggestions != null && suggestions.mouseScrolled(scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private void draw(Gfx gfx) {
		int x = left();
		int y = top();
		gfx.fill(x, y, x + WIDTH, y + dialogHeight(), Theme.PANEL);
		gfx.fill(x, y, x + WIDTH, y + 22, Theme.HEADER);
		gfx.fill(x, y + 21, x + WIDTH, y + 22, Theme.ACCENT_DIM);
		gfx.text(font, title.getString(), x + 10, y + 7, Theme.ACCENT);
		int fy = y + 28;
		for (Field field : fields) {
			gfx.text(font, field.label(), x + 10, fy, Theme.TEXT_SECONDARY);
			fy += FIELD;
		}
		if (groups != null) {
			gfx.text(font, "放在哪個分組", x + 10, fy, Theme.TEXT_SECONDARY);
			fy += FIELD;
		}
		if (!error.isEmpty()) {
			gfx.text(font, error, x + 10, fy, Theme.DANGER);
		}
	}

	@Override
	public void onClose() {
		CommandMenuScreen.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
