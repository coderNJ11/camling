from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import pipeline
from jinja2 import Template
import os
from datetime import datetime

# Initialize FastAPI app
app = FastAPI()

# Load a local or open-source pre-trained LLM using Hugging Face transformers (e.g., a GPT model)
generator = pipeline("text-generation", model="EleutherAI/gpt-neo-1.3B", device=0)  # Use device=0 if GPU is available


# Email template request structure
class EmailTemplateRequest(BaseModel):
    description: str = None  # Optional description for template generation
    template_body: str = None  # Optional provided base HTML template
    data: dict = None  # Map of key-value pairs or nested objects for dynamic population
    additional_sections: dict = None  # Additional sections for the email, e.g., {"footer": "Thank you"}
    display_as_table: bool = True  # Option to render data as a table or list
    add_placeholders: bool = True  # Option to add placeholders for undefined variables


# Main endpoint for generating an email template
@app.post("/generate-email-template/")
async def generate_email_template(request: EmailTemplateRequest):
    # Validate input
    if not request.description and not request.template_body:
        raise HTTPException(status_code=400, detail="Either 'description' or 'template_body' must be provided.")

    # Load the base email template from the HTML file
    base_template_path = "base_email_template.html"
    if not os.path.exists(base_template_path):
        raise HTTPException(status_code=500, detail="Base email template file not found.")

    with open(base_template_path, "r") as file:
        base_template_html = file.read()

    # Generate the dynamic content
    dynamic_content = ""

    # Generate template from description if provided
    if request.description:
        prompt = (
            f"Generate an HTML email template for the following description: '{request.description}'. "
            "Include placeholders for variables, and use a modern table or list format for any data section."
        )
        generated_response = generator(prompt, max_length=512, num_return_sequences=1)
        dynamic_content += generated_response[0]["generated_text"]

    # Define default data display logic
    if request.data:
        if request.display_as_table:
            # Render data in a HTML table format
            data_section = """
            <table>
                <thead>
                    <tr>
                        <th>Key</th>
                        <th>Value</th>
                    </tr>
                </thead>
                <tbody>
                    {% for key, value in data.items() %}
                    {% if value is mapping %}
                    <tr>
                        <td colspan="2"><strong>{{ key }}</strong></td>
                    </tr>
                    {% for nested_key, nested_value in value.items() %}
                    <tr>
                        <td class="nested-key">- {{ nested_key }}</td>
                        <td>{{ nested_value }}</td>
                    </tr>
                    {% endfor %}
                    {% else %}
                    <tr>
                        <td>{{ key }}</td>
                        <td>{{ value }}</td>
                    </tr>
                    {% endif %}
                    {% endfor %}
                </tbody>
            </table>
            """
        else:
            # Render data in a HTML list format
            data_section = """
            <ul>
                {% for key, value in data.items() %}
                {% if value is mapping %}
                <li>
                    <strong>{{ key }}</strong>
                    <ul>
                    {% for nested_key, nested_value in value.items() %}
                        <li>{{ nested_key }}: {{ nested_value }}</li>
                    {% endfor %}
                    </ul>
                </li>
                {% else %}
                <li>{{ key }}: {{ value }}</li>
                {% endif %}
                {% endfor %}
            </ul>
            """
        # Add data section to the dynamic content
        dynamic_content += data_section
    elif request.add_placeholders:
        dynamic_content += "<p>No data available to display.</p>"

    # Add support for additional sections (e.g., footer, header customization)
    if request.additional_sections:
        for section_name, section_content in request.additional_sections.items():
            dynamic_content += f"<div><strong>{section_name.title()}:</strong> {section_content}</div>\n"

    # Inject the dynamic content into the base template
    jinja_template = Template(base_template_html)
    rendered_html = jinja_template.render(
        dynamic_content=dynamic_content,
        current_year=datetime.now().year  # For displaying the current year in the footer
    )

    # Return the generated HTML
    return {"html_template": rendered_html}
