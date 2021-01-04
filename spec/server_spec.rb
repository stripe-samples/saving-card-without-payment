RSpec.describe "full integration path" do
  it "/ fetches the index route" do
    # Get the index html page
    response = get("/")
    expect(response).not_to be_nil
  end

  it "/public-key serves public key as expected" do
    resp = get_json("/public-key")
    expect(resp).to have_key("publicKey")
  end

  it "/create-setup-intent creates a customer and setup intent" do
    before_customer_id = Stripe::Customer.list(limit: 1).data.first.id

    resp, status = post_json("/create-setup-intent", {})

    after_customer_id = Stripe::Customer.list(limit: 1).data.first.id
    expect(before_customer_id).not_to eq(after_customer_id)
    if resp["customer"].is_a?(String)
      expect(resp["customer"]).to eq(after_customer_id)
    else # for java, we serialize the nested object with it's ID rather than returning polymorphic field.
      expect(resp["customer"]["id"]).to eq(after_customer_id)
    end
  end
end
